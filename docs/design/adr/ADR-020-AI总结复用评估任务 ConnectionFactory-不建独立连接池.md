---
adr: ADR-020
title: "AI总结复用评估任务 ConnectionFactory，不建独立连接池"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-020：AI总结复用评估任务 ConnectionFactory，不建独立连接池

| 项目 | 内容 |
|---|---|
| **上下文** | AI总结异步架构新增 `AiSummaryRabbitConfig`，初始实现照搬 `EvalRabbitConfig` 模式，创建了独立的 `aiSummaryConnectionFactory` Bean。Spring 容器中同时存在 `evalConnectionFactory` 和 `aiSummaryConnectionFactory` 两个 `CachingConnectionFactory` Bean，导致 `aiSummaryListenerContainerFactory(ConnectionFactory connectionFactory)` 参数按类型注入时发现两个候选，启动报错。删除 `aiSummaryConnectionFactory` 后，容器中出现两个 `RabbitTemplate` Bean（`evalRabbitTemplate` 和 `aiSummaryRabbitTemplate`），SDK 模块的 `CollectFullPathService` 按类型注入 `AmqpTemplate` 时发现两个候选，再次报错。 |
| **决策** | 1. 删除 `aiSummaryRabbitConfig` 中的 `aiSummaryConnectionFactory` Bean，`aiSummaryListenerContainerFactory` 和 `aiSummaryRabbitTemplate` 均注入容器中唯一的 `evalConnectionFactory`。2. 给 `evalRabbitTemplate` 添加 `@Primary` 注解，使其成为 `AmqpTemplate`/`RabbitTemplate` 类型按类型注入时的默认候选，解决 SDK 等第三方模块的注入冲突。逻辑隔离仅通过 RabbitTemplate（各自独立的 ConfirmCallback）和 ListenerContainerFactory（各自独立的 prefetchCount）实现，不再通过独立 TCP 连接隔离。 |
| **备选方案** | ① 保留两个独立 ConnectionFactory，用 `@Qualifier` 或参数名匹配解决 Bean 冲突；② 两个配置都删除独立 ConnectionFactory，统一使用 Spring Boot 自动配置的默认 ConnectionFactory；③ 不用 `@Primary`，在 SDK 的 `CollectFullPathService` 上加 `@Qualifier`（但 SDK 是外部模块，不应为某个业务的 Bean 配置修改） |
| **影响** | K8s 多副本场景下连接数减半（N副本 × 1连接 vs N副本 × 2连接）；Publisher Confirm 仍可正常工作（`evalConnectionFactory` 已开启 CORRELATED）；两个业务的 RabbitTemplate 和 ListenerContainerFactory 仍独立，互不影响；`@Primary` 确保第三方模块按类型注入时默认使用 `evalRabbitTemplate`，功能上等价于全局默认 RabbitTemplate；如果未来需要不同 RabbitMQ 实例（不同 vhost/host），则需重新引入独立 ConnectionFactory |

### ADR-020 附录：RabbitMQ 隔离层次对比

| 隔离维度 | 是否隔离 | 实现方式 |
|---|---|---|
| Exchange/Queue | ✅ 隔离 | `eval-task-exchange` vs `ai-summary-exchange` |
| RabbitTemplate | ✅ 隔离 | `evalRabbitTemplate` vs `aiSummaryRabbitTemplate`（各自独立的 Confirm/Return 回调） |
| ListenerContainerFactory | ✅ 隔离 | `evalListenerContainerFactory`(prefetch=10) vs `aiSummaryListenerContainerFactory`(prefetch=5) |
| TCP 连接 | ❌ 共享 | 同一个 `evalConnectionFactory`，K8s 友好 |

### ADR-020 附录：与 RocketMQ 的类比

| RabbitMQ 概念 | RocketMQ 类比 | 说明 |
|---|---|---|
| Connection (TCP) | MQClientInstance | 应共享，一个 TCP 连接可操作多个 Queue/Topic |
| Channel | 同一 ClientInstance 内的订阅 | 轻量级，一个 Connection 可开多个 Channel |
| Exchange + Queue | Topic | 逻辑隔离靠不同的 Exchange+Queue，类似不同 Topic |
| RabbitTemplate | DefaultMQProducer | 需要独立，因为 ConfirmCallback 是 Template 级别配置 |
| ListenerContainerFactory | ConsumerGroup 配置 | 需要独立，因为 prefetchCount 等消费策略不同 |

详细技术文档见：[RabbitMQ-ConnectionFactory共享决策-技术文档-GLM-5.1.md](../../knowledge/RabbitMQ-ConnectionFactory共享决策-技术文档-GLM-5.1.md)

详细设计文档见：[红线事件表达式参数缺失修复-设计文档-20260527.md](../p2-评估框架/红线事件表达式参数缺失修复-设计文档-20260527.md)

---
