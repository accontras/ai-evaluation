## Why

`ResilientLlmClient` 已有重试+fallback+熔断的骨架，但存在三个关键缺陷：(1) `LlmConfig` 中所有 fallback 客户端使用同一 baseUrl/apiKey，实际没有切换模型；(2) 降级不可观测——`eval_ai_experiment` 无 `degradation_level` 字段，无法回答"这次评估走了哪条降级路径"；(3) 缺乏 token 预算保护，LLM 调用不可控。需要系统化升级为真正的 AI 可靠性工程。

## What Changes

- **重构 LlmConfig + LlmProperties**：primary/fallbacks 数组配置，每个备选模型独立 apiKey/baseUrl/model
- **增强 ResilientLlmClient**：真半开探测（限 1 次探测请求）、token 预算保护、降级追踪
- **新增 degradation_level 字段**：`eval_ai_experiment` 表 + Entity，记录降级层级
- **新增可视化 API**：`GET /api/v1/ai/resilience-status`，返回熔断状态、各模型统计、降级分布
- **降级策略分层**：L1 切备选模型 → L2 用规则引擎分数 → L3 默认 70 分 + DEGRADED

## Capabilities

### New Capabilities
- `llm-resilience`: 多模型 fallback 链可配置可观测、熔断半开探测、token 预算保护、降级分层追踪、可视化状态 API

### Modified Capabilities
<!-- 无已有 capability 被修改 -->

## Impact

- **修改**：`LlmConfig.java`, `LlmProperties.java`, `ResilientLlmClient.java`, `LlmScoringStrategy.java`
- **新增**：`ResilienceController.java` (API), `EvalAiExperiment.degradationLevel` 字段
- **新增 DB 迁移**：`ALTER TABLE eval_ai_experiment ADD COLUMN degradation_level`
- **修改配置**：`application.yml` llm 配置段重构
