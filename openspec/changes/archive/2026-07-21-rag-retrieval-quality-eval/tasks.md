## 1. Ground truth 标注数据集

- [x] 1.1 从 126 条历史日志中筛选 8-12 条有代表性的查询（覆盖不同指标类型和数值范围）
- [x] 1.2 合成 5-8 条边界查询（极端值、无数据、稀疏指标）
- [x] 1.3 对每条查询标注理想 Top-3 检索结果（人工判断历史案例的相关性）
- [x] 1.4 创建 `data/rag-ground-truth.json`，含 indexCode/indexName/dataValue/relevantLogIds/notes

## 2. 数据库表 + 基础代码

- [x] 2.1 创建 `eval_rag_compare_log` DDL（按 design.md D3）
- [x] 2.2 创建 Entity：`EvalRagCompareLog`（domain 层）
- [x] 2.3 创建 Mapper：`EvalRagCompareLogMapper`（infrastructure 层）

## 3. RagCompareTracker 改造

- [x] 3.1 `RagCompareTracker` 增加 `EvalRagCompareLogMapper` 依赖，record() 方法增加 DB 写入
- [x] 3.2 保留内存计数器作为实时查询用（getStats() 不变），DB 写入同步
- [x] 3.3 验证：跑一次评估 → `eval_rag_compare_log` 有新增记录

## 4. 量化指标计算

- [x] 4.1 创建 `RagQualityService`：实现 HR@K 计算（K 可配置，默认 1/3/5）
- [x] 4.2 实现 NDCG@K 计算（二元相关，DCG/IDCG 公式）
- [x] 4.3 单元测试：HR@K 全部命中 / 全部未命中；NDCG@K 最佳排序 / 次优排序 / 全不相关

## 5. 跑批评测脚本

- [x] 5.1 创建 `RagQualityEvalRunner`（CommandLineRunner，profile=rag-eval）
- [x] 5.2 加载 ground truth JSON → 逐条调 VectorRagService.search()
- [x] 5.3 对每条查询记录检索结果 + 相关性标记（ground_truth_rel）
- [x] 5.4 汇总计算 HR@1/3/5 和 NDCG@1/3/5 → 输出 JSON 到 `data/rag-eval-results/`
- [x] 5.5 启动类 profile 配置：`spring.profiles.active=rag-eval` 时只跑评测不启 Web

## 6. 图表生成

- [x] 6.1 生成 `data/rag-eval-results/index.html`：Chart.js 柱状图（HR@K）+ 折线图（NDCG@K）
- [x] 6.2 图表包含总查询数、K 值档位标注、数值标签

## 7. 实验笔记

- [x] 7.1 撰写 `wiki/research/RAG-检索质量评测-YYYYMMDD.md`
- [x] 7.2 内容：实验目的 + 标注数据说明 + 指标结果表 + 图表截图引用 + 结论 + 改进方向
- [x] 7.3 同步更新 `eval-system/docs/AI-KNOWLEDGE-REF.md` 添加指向实验笔记的链接
