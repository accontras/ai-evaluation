---
adr: ADR-011
title: "AI 总结 Handler 必须编写，MVP 阶段逻辑放空"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-011：AI 总结 Handler 必须编写，MVP 阶段逻辑放空

| 项目 | 内容 |
|---|---|
| **上下文** | AI 评估总结是数据飞轮的核心点，但当前 MVP 阶段 AI 服务尚未就绪。如果不预留 Handler 位置，后续集成时需要改动管道结构。 |
| **决策** | `SummarizeResultHandler` 必须编写，AI 调用逻辑预留接口位，MVP 阶段 `summary` 返回 null。 |
| **备选方案** | ① MVP 不写 Handler，后续再加；② 用空实现（NoOp Handler）占位 |
| **影响** | 管道结构从 MVP 开始就稳定，后续 AI 就绪只需填充实现；MVP 阶段用户看到 summary 为 null 需要前端做好兜底展示 |

---
