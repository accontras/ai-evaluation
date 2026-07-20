---
adr: ADR-021
title: "DataPullService 取数策略重构：viewCode 分组驱动，替代逐指标循环"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-021：DataPullService 取数策略重构：viewCode 分组驱动，替代逐指标循环

| 项目 | 内容 |
|---|---|
| **上下文** | DataPullService 路径 B 当前对每个 IndexBase 循环调用 `openDataWidgetApi/list`，但同一个 viewCode 可能对应多个指标，导致重复调用。且 `openDataWidgetApi/list` 返回的是一张宽表（一个 View = 多个指标的合集），本质是"指标组取数"而非"逐指标取数"。此外，Stage 分组（评分聚合维度）与 viewCode 分组（数据获取维度）语义正交，不应混用。 |
| **决策** | 取数策略改为 viewCode 分组驱动：① 加载所有 IndexBase 后按 `queryDataSet`（viewCode）分组；② 同 viewCode 的指标合并为一次 `openDataWidgetApi/list` 调用；③ 返回宽表全量 merge 到 `RawData.fields`，不做按 metric 拆分；④ 无 viewCode 但有 `target.dataSet` 的指标走 `queryMetricFromSql` 兜底（过渡方案）；⑤ 类拆分为 DataPullService（编排）+ GroupViewDataPuller（指标组取数）+ MetricDataPuller（单指标取数，过渡）。 |
| **备选方案** | ① 保持逐指标循环，仅做 viewCode 去重缓存；② 用 Stage 分组驱动取数（Stage 与 viewCode 语义不匹配）；③ 完全移除 `queryMetricFromSql`（对接方尚未就绪） |
| **影响** | 同 viewCode 只调一次 data-view，减少 N-1 次冗余调用；DataPullService 从 900+ 行拆分为 3 个类，职责清晰；`queryMetricFromSql` 保留为过渡兜底，对接方 viewCode 接口就绪后可移除；全量 merge 策略与下游 Handler2 兼容，无需改动 |

### ADR-021 附录：路径优先级

| 优先级 | 路径 | 触发条件 | 接口 | 定位 |
|---|---|---|---|---|
| 1 | A | `request.data` 非空 | 无 | 直接使用 |
| 2 | B | `IndexBase.queryDataSet` 非空 | `openDataWidgetApi/list` | 主力通道 |
| 3 | C | `queryDataSet` 为空 且 `target.dataSet` 非空 | `queryMetricFromSql` | 过渡兜底 |

路径 B 和 C 可共存，结果统一 merge。

详细设计见 DataPullService 取数策略重构相关文档。
