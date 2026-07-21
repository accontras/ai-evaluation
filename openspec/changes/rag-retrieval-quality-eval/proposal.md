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
