# Brainstorm Summary

- Change: rag-retrieval-quality-eval
- Date: 2026-07-22

## 确认的技术方案

**架构**：两条路径 — 运行时路径（每次评估 → `RagCompareTracker` 同步写 DB） + 评测路径（Spring profile=rag-eval → `RagQualityEvalRunner` 加载 ground truth → 双通道评测 → 输出 JSON + HTML 图表）

**数据模型**：
- `ground_truth_rel` 采用**并行数组**，与 `vector_results` 一一对应，跑批时用 `relevantLogIds.contains()` 自动生成
- Ground truth 存 JSON 文件 (`data/rag-ground-truth.json`)，包含 `id/indexCode/indexName/dataValue/relevantLogIds/notes`
- DB 表 `eval_rag_compare_log` 中 `ground_truth_rel` 运行时为 NULL，评测时填充

**评测范围**：向量 + 规则**双通道**都计算 HR@K / NDCG@K

**图表生成**：Java 代码直接写 HTML 字符串，Chart.js CDN

## 关键取舍与风险

- 二元制标注：简单但丢失 nuance，实验笔记中透明披露
- DB 同步写入：避免异步丢数据，评估本身是 MQ 异步的
- Spring profile 跑批：启动慢 ~5s 但零额外代码
- Chart.js CDN 离线不可用：A5 再说

## 测试策略

- 单元测试：RagQualityService.hrAtK() / ndcgAtK() — 全命中、全不中、部分命中、空输入
- 集成测试：RagQualityEvalRunner + mock ground truth
- 手动验证：HTML 图表 + 实验笔记通读

## Spec Patch

无
