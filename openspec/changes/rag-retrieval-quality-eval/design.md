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
HR@K = (命中查询数) / (总查询数)
命中 = Top-K 结果中至少 1 个 ID 在 ground truth 相关集合中
```

**NDCG@K (Normalized Discounted Cumulative Gain)**：
```
DCG@K  = Σ(i=1→K) (2^rel_i - 1) / log₂(i+1)
IDCG@K = 理想排序下的 DCG@K（所有相关文档排最前）
NDCG@K = DCG@K / IDCG@K
```
二元相关下 `rel_i ∈ {0, 1}`，`2^rel_i - 1 ∈ {0, 1}`。

**示例**：
```
查询: "拜访次数=15, 指标=VISIT_COUNT"
向量返回 Top-3: [logId:101(relevant), logId:203(not), logId:45(not)]
                  rel = [1, 0, 0]

DCG@3  = 1/log₂(2) + 0/log₂(3) + 0/log₂(4) = 1.0
IDCG@3 = 1/log₂(2) + 1/log₂(3) + 1/log₂(4) ≈ 1.63 (假设理想排序有3个相关)
NDCG@3 = 1.0 / 1.63 ≈ 0.61
```

### D5: 跑批脚本用纯 Java main 方法

**选择**：Spring Boot 非 Web 模式启动 + `CommandLineRunner`，从 JSON 读 ground truth，调 `VectorRagService` 检索，计算指标，输出图表。

**理由**：
- 复用已有 `VectorRagService`，不需要重新实现检索逻辑
- 一次启动跑完退出，适合 CI/定时任务
- 输出结果到 `eval-system/data/rag-eval-results/`

**替代方案**：Python 脚本 → 需要独立实现 embedding 和检索，重复工作量大。

### D6: 图表用 ASCII chart 或简单 HTML

**选择**：跑批脚本输出 JSON 数据 + 生成简单 HTML 页面（Chart.js CDN）。

**理由**：
- Chart.js 已在 Dashboard 中使用，风格一致
- HTML 可直接在浏览器打开，无需额外工具
- 实验笔记引用图表截图或链接

## Risks / Trade-offs

- **[风险] 标注质量低**：10-20 条自己标注，可能偏向自己理解的"相关" → **缓解**：标注时记录标注理由，实验笔记中透明说明标注者偏见
- **[风险] 126 条历史数据不足以覆盖多样性**：大部分是同场景的重复 → **缓解**：合成 5-8 条边界查询补充
- **[取舍] 不做 A/B 测试**：只做离线评测，不做在线对比实验 → 因为评估系统没有真实用户流量
