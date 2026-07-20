---
adr: ADR-004
title: "Handler 顺序显式写死列表，不使用 @Order 注解"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-004：Handler 顺序显式写死列表，不使用 @Order 注解

| 项目 | 内容 |
|---|---|
| **上下文** | 管道中 Handler 的执行顺序是核心业务逻辑，需要一目了然、不易出错。 |
| **决策** | 在 `@PostConstruct init()` 中用 `Arrays.asList(...)` 显式声明 Handler 列表及顺序。 |
| **备选方案** | 使用 Spring `@Order` 注解让容器自动排序注入 |
| **影响** | 管道顺序在一个地方即可看完，新增 Handler 只需加一行；代价是与 Spring 解耦不够彻底，但评估管道不需要插件化扩展 |

---
