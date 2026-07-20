---
adr: ADR-005
title: "红线检测内嵌于 CalculateScoresHandler，不拆分为独立 Handler"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-005：红线检测内嵌于 CalculateScoresHandler，不拆分为独立 Handler

| 项目 | 内容 |
|---|---|
| **上下文** | 红线命中后的行为是"审核标记+修正得分"，而非"短路跳过后续步骤"。如果拆为独立 Handler，会增加管道步骤但不会带来流程控制价值。 |
| **决策** | 红线检测逻辑保留在 `CalculateScoresHandler` 内部，命中时标记风险、修正得分、写入 risk_tags。 |
| **备选方案** | ① 红线前置短路（命中后跳过后续计算）；② 拆分为独立 `RedLineCheckHandler` |
| **影响** | 管道步骤数保持精简；红线逻辑与得分计算紧密耦合，但二者本身就是同一业务步骤的不同侧面。后续如需"跨指标组合红线事件"，仍在 CalculateScoresHandler 末尾统一判定 |

---
