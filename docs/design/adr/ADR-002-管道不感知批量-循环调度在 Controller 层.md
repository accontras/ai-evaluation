---
adr: ADR-002
title: "管道不感知批量，循环调度在 Controller 层"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-002：管道不感知批量，循环调度在 Controller 层

| 项目 | 内容 |
|---|---|
| **上下文** | 批量评估时，每个 bizId 的评估流程完全一致。如果 Handler 内部感知批量，会导致每个 Handler 都要处理循环逻辑，增加复杂度且难以维护。 |
| **决策** | Handler 1~4 始终处理单个 bizId。Controller 层完成 `foreach` 循环调度，管道内部逻辑完全一致。 |
| **备选方案** | ① Handler 内部支持批量（传入 List\<bizId\>）；② 引入批量编排器在管道外层包装 |
| **影响** | Handler 实现简单、可独立测试；批量并发控制集中在 Controller，后续如需并行化只需改 Controller 的循环方式（如并行流/线程池） |

---
