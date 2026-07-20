---
adr: ADR-006
title: "DATA-PULL 只在 Controller 执行一次，Handler2 纯内存操作"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-006：DATA-PULL 只在 Controller 执行一次，Handler2 纯内存操作

| 项目 | 内容 |
|---|---|
| **上下文** | 批量评估时，如果每个 Handler 各自拉取数据，会产生 N 次重复的网络/DB 调用。且维度分配需要全量数据才能按 bizId 拆分。 |
| **决策** | 数据拉取和维度分配在 Controller 层一次性完成，输出 `List<EvaluationData>`。`FetchIndicatorValuesHandler`（Handler2）只从已拉取的 rawData 中纯内存提取，不做任何 IO。 |
| **备选方案** | ① Handler2 内部按需拉取每个 bizId 的数据；② 懒加载，首次访问时拉取并缓存 |
| **影响** | 批量场景下数据只拉一次，性能最优；Handler2 可纯单元测试（mock rawData）；代价是 Controller 层职责较重，但数据拉取本身就是编排层的职责 |

---
