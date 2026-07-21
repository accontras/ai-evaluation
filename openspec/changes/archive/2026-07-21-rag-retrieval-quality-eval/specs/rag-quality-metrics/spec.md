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
