---
adr: ADR-017
title: "评估任务消息启用 Publisher Confirm 异步确认，专用 Template 隔离"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-017：评估任务消息启用 Publisher Confirm 异步确认，专用 Template 隔离

| 项目 | 内容 |
|---|---|
| **上下文** | 批量评估通过 MQ 异步执行，Producer 使用 `RabbitTemplate.convertAndSend()` 发送消息，这是 fire-and-forget 模式。实测发现 22 条消息中 1 条（bizId=00774748）被静默丢失，Producer 无感知，导致评估记录 PARTIAL_FAIL。项目中同时存在非关键消息（如日志），不需要 Confirm 开销。 |
| **决策** | 为评估任务消息创建专用 `evalRabbitTemplate`，开启 `publisher-confirm-type=correlated` 异步确认模式。发送时为每条消息关联 `CorrelationData`（绑定 logBaseId），Broker 异步回调确认结果。发送完成后等待所有确认（超时 10s），失败消息重试 1 次，仍失败则记录 ERROR 日志并标记 bizId。全局默认 `RabbitTemplate` 保持不变，不影响非关键消息的性能。 |
| **备选方案** | ① 全局 RabbitTemplate 开启 Confirm（影响非关键消息性能，违反关注点分离）；② 同步逐条 Confirm（5000 条耗时 50s+，不可接受）；③ 批量 Confirm（只知批次不知哪条失败，无法精确定位问题） |
| **影响** | 评估消息投递可靠性从"尽力而为"提升到"确认+重试"；消息丢失时可精确定位到具体 bizId 和失败原因（网络/Channel/路由）；专用 Template 与全局 Template 隔离，互不影响；5000 条消息异步确认约 2-3 秒完成，不阻塞发送流程 |

---
