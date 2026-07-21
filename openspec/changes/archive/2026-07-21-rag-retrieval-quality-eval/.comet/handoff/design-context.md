# Comet Design Handoff

- Change: rag-retrieval-quality-eval
- Phase: design
- Mode: compact
- Context hash: f224b92ab3731614048de9e2efb1d600f3b2e6555c7637a9eb17a2eecd0625e9

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/rag-retrieval-quality-eval/proposal.md

- Source: openspec/changes/rag-retrieval-quality-eval/proposal.md
- Lines: 1-28
- SHA256: d86db1026b3782363f96527f8c1613d63e5cee8c05f8bae2d066e6a698820c30

```md
## Why

A3.1（Embedding + 向量存储）和 A3.2（检索 + 注入 Prompt）已跑通 RAG pipeline 端到端。但检索质量仍是黑盒——不知道向量检索到底找没找对、比规则检索好多少。当前 `RagCompareTracker` 只统计"是否有返回"，不衡量"返回是否正确"。需要量化评测来回答：**这个 RAG pipeline 到底靠不靠谱？**

## What Changes

- **新增检索质量指标计算**：Hit Rate@K (K=1,3,5) 和 NDCG@K (K=1,3,5)，衡量向量检索返回结果的相关性
- **新增人工标注数据集**：从历史 126 条数据 + 合成查询混合选取 10-20 条，标注理想 Top-3 检索结果作为 ground truth
- **新增 `eval_rag_compare_log` 表**：持久化每次评估的向量 vs 规则对比数据（替代当前内存 AtomicLong）
- **新增跑批评测脚本**：基于标注数据集批量执行检索评测，计算 HR@K / NDCG@K
- **输出图表**：HR@K 柱状图 + NDCG@K 折线图
- **输出实验笔记**：`wiki/research/RAG-检索质量评测-{日期}.md`，含实验设计、数据、结论

## Capabilities

### New Capabilities
- `rag-quality-metrics`: 向量检索质量量化评测，含标注数据集、HR@K/NDCG@K 计算、对比数据持久化、跑批脚本与可视化

### Modified Capabilities
<!-- 无已有 capability 被修改 -->

## Impact

- **新增 DB 表**：`eval_rag_compare_log`
- **新增 Java 类**：`RagCompareLog` (Entity)、`RagCompareLogMapper`、`RagQualityService`（评测逻辑）、`RagComparePersistenceService`（持久化）
- **修改**：`RagCompareTracker` → 增加持久化写入；新增 `/api/v1/rag-quality/evaluate` 评测 API
- **新增脚本**：跑批评测 + 图表生成
- **新增文档**：`wiki/research/RAG-检索质量评测-YYYYMMDD.md`
```

## openspec/changes/rag-retrieval-quality-eval/design.md

- Source: openspec/changes/rag-retrieval-quality-eval/design.md
- Lines: 1-129
- SHA256: 47c4bd9ce8c31bef14a108d1e371b03ca74e080e1b352f4e724be31d9bfcdc62

[TRUNCATED]

```md
## Context

A3.1 和 A3.2 已完成 RAG pipeline 端到端实现：
- `EmbeddingService`：ONNX Runtime + bge-small-zh-v1.5，3-5ms encode
- `VectorIndexService`：Lucene HNSW 索引，6-11ms search
- `VectorRagService`：编排层，每指标 Top-2 → 合并去重 Top-3
- `LlmScoringStrategy.buildFewShot()`：向量优先 → 规则降级 → 空 safe fallback

当前 `RagCompareTracker` 是纯内存 AtomicLong 计数器，只能统计"是否有返回"，无法衡量**检索结果是否相关**。需要引入 ground truth 标注和标准检索质量指标。

## Goals / Non-Goals

**Goals:**
- 建立 10-20 条标注查询的 ground truth 数据集
- 实现 HR@K (K=1,3,5) 和 NDCG@K (K=1,3,5) 计算
- 持久化运行时对比数据到 `eval_rag_compare_log` 表
- 跑批脚本：基于标注集批量评测 → 输出图表
- 输出实验笔记到 `wiki/research/`

**Non-Goals:**
- 不做实时 dashboard（太复杂，等后续 A5 再说）
- 不加 MRR/MAP（保持计划范围）
- 不改 RAG pipeline 本身的检索逻辑（只评测，不优化）

## Decisions

### D1: 相关性标注用二元制（相关/不相关）

**选择**：二元相关（0/1），不用多级（0/1/2/3）。

**理由**：
- 标注成本低：标注者只需判断"这个历史案例对当前查询有没有参考价值"
- NDCG 用二元相关完全有效
- 10-20 条小数据集下，多级标注的粒度优势体现不出来

**替代方案**：多级相关（完全不相关/部分相关/相关/高度相关）→ 标注更主观，小数据集下噪声大。

### D2: Ground truth 存储在 JSON 文件中

**选择**：`eval-system/data/rag-ground-truth.json`，不建 DB 表。

**理由**：
- ground truth 是一次性标注产物，不是运行时数据
- JSON 文件便于版本管理（git diff 可读）
- 跑批脚本直接读取，无需 DB 连接

**替代方案**：DB 表存储 → 过度设计，10-20 条数据不需要。

### D3: 对比数据持久化到新表 `eval_rag_compare_log`

**选择**：新建表，与 `eval_ai_experiment` 平级，不做外键关联。

```sql
CREATE TABLE eval_rag_compare_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_id      VARCHAR(64)  COMMENT '评估对象ID',
    scene_code  VARCHAR(64)  COMMENT '场景编码',
    index_code  VARCHAR(64)  COMMENT '指标编码',
    index_name  VARCHAR(128) COMMENT '指标名称',
    data_value  VARCHAR(255) COMMENT '指标实际值',
    vector_results JSON      COMMENT '向量检索返回的案例ID列表 [id1,id2,id3]',
    rule_results   JSON      COMMENT '规则检索返回的案例ID列表 [id1,id2,id3]',
    vector_similarities JSON COMMENT '向量检索相似度 [0.85, 0.72, 0.61]',
    vector_hit  TINYINT(1)   COMMENT '向量是否有返回',
    rule_hit    TINYINT(1)   COMMENT '规则是否有返回',
    ground_truth_rel JSON     COMMENT '标注相关性 (仅评测模式, 运行时NULL)',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scene_biz (scene_code, biz_id),
    INDEX idx_created (created_at)
) COMMENT 'RAG 检索对比日志';
```

**理由**：
- JSON 字段存结果列表，灵活不僵化
- ground_truth_rel 只在跑批评测时填充，运行时为 NULL
- 索引覆盖常用查询维度

### D4: HR@K 和 NDCG@K 计算逻辑

**HR@K (Hit Rate at K)**：Top-K 中至少有一个相关文档的查询占比。
```

Full source: openspec/changes/rag-retrieval-quality-eval/design.md

## openspec/changes/rag-retrieval-quality-eval/tasks.md

- Source: openspec/changes/rag-retrieval-quality-eval/tasks.md
- Lines: 1-43
- SHA256: 274491f256b62bfa7a2eaa494457abd3c88101b5653052fbdf0d6f43245b047a

```md
## 1. Ground truth 标注数据集

- [ ] 1.1 从 126 条历史日志中筛选 8-12 条有代表性的查询（覆盖不同指标类型和数值范围）
- [ ] 1.2 合成 5-8 条边界查询（极端值、无数据、稀疏指标）
- [ ] 1.3 对每条查询标注理想 Top-3 检索结果（人工判断历史案例的相关性）
- [ ] 1.4 创建 `data/rag-ground-truth.json`，含 indexCode/indexName/dataValue/relevantLogIds/notes

## 2. 数据库表 + 基础代码

- [ ] 2.1 创建 `eval_rag_compare_log` DDL（按 design.md D3）
- [ ] 2.2 创建 Entity：`EvalRagCompareLog`（domain 层）
- [ ] 2.3 创建 Mapper：`EvalRagCompareLogMapper`（infrastructure 层）

## 3. RagCompareTracker 改造

- [ ] 3.1 `RagCompareTracker` 增加 `EvalRagCompareLogMapper` 依赖，record() 方法增加 DB 写入
- [ ] 3.2 保留内存计数器作为实时查询用（getStats() 不变），DB 写入异步/同步可选
- [ ] 3.3 验证：跑一次评估 → `eval_rag_compare_log` 有新增记录

## 4. 量化指标计算

- [ ] 4.1 创建 `RagQualityService`：实现 HR@K 计算（K 可配置，默认 1/3/5）
- [ ] 4.2 实现 NDCG@K 计算（二元相关，DCG/IDCG 公式）
- [ ] 4.3 单元测试：HR@K 全部命中 / 全部未命中；NDCG@K 最佳排序 / 次优排序 / 全不相关

## 5. 跑批评测脚本

- [ ] 5.1 创建 `RagQualityEvalRunner`（CommandLineRunner，profile=rag-eval）
- [ ] 5.2 加载 ground truth JSON → 逐条调 VectorRagService.search()
- [ ] 5.3 对每条查询记录检索结果 + 相关性标记（ground_truth_rel）
- [ ] 5.4 汇总计算 HR@1/3/5 和 NDCG@1/3/5 → 输出 JSON 到 `data/rag-eval-results/`
- [ ] 5.5 启动类 profile 配置：`spring.profiles.active=rag-eval` 时只跑评测不启 Web

## 6. 图表生成

- [ ] 6.1 生成 `data/rag-eval-results/index.html`：Chart.js 柱状图（HR@K）+ 折线图（NDCG@K）
- [ ] 6.2 图表包含总查询数、K 值档位标注、数值标签

## 7. 实验笔记

- [ ] 7.1 撰写 `wiki/research/RAG-检索质量评测-YYYYMMDD.md`
- [ ] 7.2 内容：实验目的 + 标注数据说明 + 指标结果表 + 图表截图引用 + 结论 + 改进方向
- [ ] 7.3 同步更新 `eval-system/docs/AI-KNOWLEDGE-REF.md` 添加指向实验笔记的链接
```

## openspec/changes/rag-retrieval-quality-eval/specs/rag-quality-metrics/spec.md

- Source: openspec/changes/rag-retrieval-quality-eval/specs/rag-quality-metrics/spec.md
- Lines: 1-70
- SHA256: bd976c2fa9413626490f998be70d83690813a62d897f7a76d0c7ff76d57c1625

```md
## ADDED Requirements

### Requirement: Ground truth 标注数据集
系统 SHALL 支持从 JSON 文件加载人工标注的检索评测 ground truth 数据集。每条标注包含：查询指标编码、查询指标名称、指标实际值、理想检索结果 Top-3 的历史日志 ID 列表。

#### Scenario: 加载标注数据集
- **WHEN** 跑批脚本启动并读取 `data/rag-ground-truth.json`
- **THEN** 系统解析出 10-20 条标注查询，每条包含 `indexCode`, `indexName`, `dataValue`, `relevantLogIds` 字段

#### Scenario: 标注文件格式校验
- **WHEN** ground truth JSON 文件缺失必填字段或 `relevantLogIds` 为空
- **THEN** 系统拒绝加载并输出明确的错误信息

### Requirement: 检索对比数据持久化
系统 SHALL 将每次评估的向量检索 vs 规则检索对比结果持久化到 `eval_rag_compare_log` 表，替代当前纯内存的 `RagCompareTracker`。

#### Scenario: 运行时持久化
- **WHEN** 评估流水线执行 `LlmScoringStrategy.buildFewShot()` 完成影子模式双跑
- **THEN** 系统向 `eval_rag_compare_log` 写入一条记录，包含 bizId、indexCode、vector_results（检索到的 logId 列表）、rule_results、相似度、命中标记

#### Scenario: 评测模式持久化
- **WHEN** 跑批脚本基于 ground truth 查询执行检索评测
- **THEN** 系统写入记录时额外填充 `ground_truth_rel` 字段（每个检索结果的相关性标记 0/1）

### Requirement: Hit Rate@K 计算
系统 SHALL 计算向量检索的 Hit Rate@K（K=1,3,5），定义为 Top-K 结果中至少包含一个相关文档的查询占比。

#### Scenario: 全部命中
- **WHEN** 10 条标注查询中，8 条的 Top-3 结果至少包含 1 个相关文档
- **THEN** HR@3 = 80.0%

#### Scenario: 部分命中
- **WHEN** 某查询的 Top-5 完全无相关文档
- **THEN** 该查询对 HR@1、HR@3、HR@5 均计为 0

### Requirement: NDCG@K 计算
系统 SHALL 计算向量检索的 NDCG@K（K=1,3,5），使用二元相关标注（相关=1，不相关=0）。NDCG = DCG / IDCG，其中 DCG 折损因子为 log₂(rank+1)。

#### Scenario: 最佳排序
- **WHEN** 某查询的 Top-3 结果全为相关文档（rel=[1,1,1]）
- **THEN** NDCG@3 = 1.0（实际排序等于理想排序）

#### Scenario: 次优排序
- **WHEN** 某查询 Top-3 结果为 [相关, 不相关, 不相关]（rel=[1,0,0]），IDCG 假设有 3 个相关文档
- **THEN** NDCG@3 < 1.0 且 > 0

### Requirement: 跑批评测脚本
系统 SHALL 提供跑批评测脚本，从 ground truth JSON 加载标注数据，逐条执行向量检索，计算并输出 HR@K 和 NDCG@K 汇总指标。

#### Scenario: 批量评测
- **WHEN** 执行 `RagQualityEvalRunner`（Spring Boot CommandLineRunner）
- **THEN** 系统加载所有标注查询 → 逐条调 `VectorRagService.search()` → 计算 HR@1/3/5 和 NDCG@1/3/5 → 输出 JSON 结果到 `data/rag-eval-results/`

#### Scenario: 向量不可用时的处理
- **WHEN** EmbeddingService 或 VectorIndexService 不可用
- **THEN** 跑批脚本输出错误信息并退出，不生成虚假的评测数据

### Requirement: 评测结果可视化
系统 SHALL 基于跑批评测的 JSON 输出生成 HTML 图表页面，包含 HR@K 柱状图和 NDCG@K 折线图。

#### Scenario: 图表生成
- **WHEN** 跑批脚本完成评测
- **THEN** 系统生成 `data/rag-eval-results/index.html`，包含 Chart.js 柱状图（HR@1/3/5）和折线图（NDCG@1/3/5），可在浏览器查看

### Requirement: 实验笔记输出
系统 SHALL 在 `wiki/research/` 目录生成 RAG 检索质量评测实验笔记，包含实验设计、标注数据集说明、指标结果、结论和后续改进方向。

#### Scenario: 笔记生成
- **WHEN** 跑批评测完成且图表生成完毕
- **THEN** 系统或人工在 `wiki/research/RAG-检索质量评测-YYYYMMDD.md` 创建笔记，至少包含：实验目的、标注数据统计、HR@K 和 NDCG@K 数值表、图表引用、结论
```

