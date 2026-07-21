---
comet_change: rag-retrieval-quality-eval
role: technical-design
canonical_spec: openspec
archived-with: 2026-07-21-rag-retrieval-quality-eval
status: final
---

# RAG 检索质量评测 — 技术设计

> A3.3：对 A3.1/A3.2 已完成的 RAG pipeline 进行检索质量量化评测。

## 1. 架构概览

两条路径共享同一套核心服务，通过 `ground_truth_rel` 字段的有无区分运行时和评测模式。

```
运行时路径                          评测路径
(每次评估触发)                       (手动/CI 跑批)

LlmScoringStrategy                 RagQualityEvalRunner
  .buildFewShot()                    (profile=rag-eval)
       │                                  │
       ▼                                  ▼
RagCompareTracker                  加载 rag-ground-truth.json
  .record()                              │
   ├─ 内存计数 (保留)                     ▼
   └─ DB 写入 (新增)               逐条调 VectorRagService.search()
       │                              + SimilarCaseService.findSimilar()
       ▼                                  │
eval_rag_compare_log                      ▼
  (ground_truth_rel = NULL)         RagQualityService
                                        .hrAtK() / .ndcgAtK()
                                        (向量 + 规则 双通道)
                                             │
                                             ▼
                                        data/rag-eval-results/
                                          results.json + index.html
```

## 2. 数据模型

### 2.1 Ground Truth (`data/rag-ground-truth.json`)

人工标注的评测基准，JSON 数组，每项一条查询：

```json
[
  {
    "id": "gt-001",
    "indexCode": "VISIT_COUNT",
    "indexName": "拜访次数",
    "dataValue": "15",
    "relevantLogIds": [101, 203, 45],
    "notes": "正常拜访频率"
  }
]
```

`relevantLogIds` 是 `eval_indicator_log.id` 的列表，标注者判断该历史案例对当前查询有参考价值即为"相关"。

### 2.2 对比日志表 (`eval_rag_compare_log`)

```sql
CREATE TABLE eval_rag_compare_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_id        VARCHAR(64)   COMMENT '评估对象ID',
    scene_code    VARCHAR(64)   COMMENT '场景编码',
    index_code    VARCHAR(64)   COMMENT '指标编码',
    index_name    VARCHAR(128)  COMMENT '指标名称',
    data_value    VARCHAR(255)  COMMENT '指标实际值',
    vector_results        JSON  COMMENT '向量检索返回的logId列表 [id1,id2,id3]',
    rule_results          JSON  COMMENT '规则检索返回的logId列表 [id1,id2,id3]',
    vector_similarities   JSON  COMMENT '向量检索相似度 [0.85,0.72,0.61]',
    vector_hit    TINYINT(1)    COMMENT '向量是否有返回',
    rule_hit      TINYINT(1)    COMMENT '规则是否有返回',
    ground_truth_rel      JSON  COMMENT '并行数组相关性标记 [1,0,0] (评测模式, 运行时NULL)',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scene_biz (scene_code, biz_id),
    INDEX idx_created (created_at)
) COMMENT 'RAG 检索对比日志';
```

`ground_truth_rel` 与 `vector_results` 是**并行数组**（方案 A），长度相同，位置一一对应：
- `1` = 该检索结果在 relevantLogIds 中（相关）
- `0` = 不相关

## 3. 核心算法

### 3.1 Hit Rate@K

```
HR@K = |{ q ∈ Q : |Ret(q, K) ∩ Rel(q)| ≥ 1 }| / |Q|
```

即 Top-K 结果中至少包含一个相关文档的查询占比。

### 3.2 NDCG@K

二元相关（rel ∈ {0,1}），折损因子 = log₂(rank+1)：

```
DCG@K  = Σ(i=1→K) rel_i / log₂(i+1)
IDCG@K = 理想排序的 DCG@K (所有相关文档排最前)
NDCG@K = DCG@K / IDCG@K
```

边界情况：
- DCG@K = 0 且 IDCG@K = 0（无相关文档）→ NDCG@K = 1.0（完美：没有该返回的都没返回）
- 检索结果数 < K：只计算实际返回的排名位置

### 3.3 双通道评测

同一份 ground truth，分别对向量检索和规则检索计算 HR@K 和 NDCG@K。输出对比表：

| 指标 | 向量 HR@3 | 规则 HR@3 | 向量 NDCG@3 | 规则 NDCG@3 |
|------|----------|----------|------------|------------|
| ... | ... | ... | ... | ... |

## 4. 新增 Java 类

| 类 | 层 | 职责 |
|----|-----|------|
| `EvalRagCompareLog` | domain | Entity，映射 `eval_rag_compare_log` |
| `EvalRagCompareLogMapper` | infrastructure | MyBatis-Plus Mapper |
| `RagQualityService` | application | HR@K / NDCG@K 纯计算，无状态，可直接单测 |
| `RagQualityEvalRunner` | boot | CommandLineRunner，profile=rag-eval 时启动 |
| `RagCompareTracker` | application（修改） | record() 增加 DB 写入 |

## 5. RagCompareTracker 改造

```java
// 现有
public void record(String bizId, boolean vectorHas, boolean ruleHas,
                   int vectorChars, int ruleChars) {
    // ... 内存计数 ...
}

// 改造后
public void record(String bizId, String sceneCode, String indexCode, 
                   String indexName, String dataValue,
                   List<Long> vectorIds, List<Long> ruleIds,
                   List<Double> similarities,
                   boolean vectorHas, boolean ruleHas) {
    // 1. 内存计数（保留，getStats() 实时查询用）
    // 2. DB 写入（同步，评估在 MQ 异步线程中，几 ms 写入不影响吞吐）
    EvalRagCompareLog log = buildLog(bizId, sceneCode, indexCode, ...);
    mapper.insert(log);
}
```

## 6. 跑批脚本工作流

```
启动 (profile=rag-eval)
  │
  ├─ 1. 检查 EmbeddingService / VectorIndexService 可用性
  │     └─ 不可用 → 输出错误，exit(1)
  │
  ├─ 2. 加载 data/rag-ground-truth.json
  │     └─ 格式错误 → 输出错误，exit(1)
  │
  ├─ 3. 逐条查询评测
  │     for each gt in groundTruths:
  │       vectorResults = vectorRagService.search(gt.indexCode, gt.indexName, gt.dataValue, 3)
  │       ruleResults = similarCaseService.findSimilar(gt.indexCode, gt.dataValue, 3)
  │       relevance = computeRelevance(vectorResults, gt.relevantLogIds)  // 并行数组
  │       save to eval_rag_compare_log (含 ground_truth_rel)
  │
  ├─ 4. 汇总计算
  │     vectorHR = hrAtK(allResults, kValues)   // [1,3,5]
  │     ruleHR = hrAtK(allResults, kValues)
  │     vectorNDCG = ndcgAtK(allResults, kValues)
  │     ruleNDCG = ndcgAtK(allResults, kValues)
  │
  ├─ 5. 输出 data/rag-eval-results/results.json
  │
  └─ 6. 生成 data/rag-eval-results/index.html (Chart.js 图表)
       └─ exit(0)
```

## 7. 文件变更清单

| 操作 | 文件 | 说明 |
|------|------|------|
| 新增 | `data/rag-ground-truth.json` | 人工标注 |
| 新增 | `eval-domain/.../EvalRagCompareLog.java` | Entity |
| 新增 | `eval-infrastructure/.../EvalRagCompareLogMapper.java` | Mapper |
| 新增 | `eval-application/.../RagQualityService.java` | 指标计算 |
| 新增 | `eval-boot/.../RagQualityEvalRunner.java` | 跑批 |
| 修改 | `eval-application/.../RagCompareTracker.java` | +DB 写入 |
| 修改 | `eval-application/.../LlmScoringStrategy.java` | record() 传更多字段 |
| 新增 | `src/main/resources/db/migration/Vxx__rag_compare_log.sql` | DDL |
| 新增 | `data/rag-eval-results/results.json` | 跑批输出 |
| 新增 | `data/rag-eval-results/index.html` | 图表 |
| 新增 | `wiki/research/RAG-检索质量评测-20260722.md` | 实验笔记 |
