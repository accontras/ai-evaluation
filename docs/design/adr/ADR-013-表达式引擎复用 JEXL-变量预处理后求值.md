---
adr: ADR-013
title: "表达式引擎复用 JEXL，变量预处理后求值"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-013：表达式引擎复用 JEXL，变量预处理后求值

| 项目 | 内容 |
|---|---|
| **上下文** | 计算规则（如 `calculate_rule` 中的 formula）需要动态表达式求值能力。项目已有 `ExpressionUtil` 基于 JEXL 实现。 |
| **决策** | 复用已有 `ExpressionUtil`（JEXL），跨指标变量（`${val}`、`${attr.xxx}`、`${idx.xxx.value}`）在求值前预处理替换为实际值。 |
| **备选方案** | ① 引入 SpEL（Spring Expression Language）；② 引入 Aviator；③ 自写解析器 |
| **影响** | 零新增依赖，与项目现有技术栈一致；JEXL 功能足够覆盖当前需求；变量预处理确保表达式引擎只做纯数学运算，不感知业务语义 |

---
