---
adr: ADR-009
title: "聚合模式多级 Fallback（Stage → Model → 默认）"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-009：聚合模式多级 Fallback（Stage → Model → 默认）

| 项目 | 内容 |
|---|---|
| **上下文** | 不同粒度（分组级、模型级）可能需要不同的聚合策略。如果只在一个层级配置，灵活性不足；如果每层都强制配置，使用成本高。 |
| **决策** | 聚合模式优先取 `dr_model_stage.aggregate_mode`；为空则取 `dr_model_base.aggregate_mode`；仍为空默认 `weighted_sum`。 |
| **备选方案** | ① 只在模型级配置，分组级不允许覆盖；② 每层都强制配置，无默认值 |
| **影响** | 常见场景零配置（默认 weighted_sum），特殊分组可单独覆盖；Fallback 链路需要文档明确，否则排查时容易困惑 |

---
