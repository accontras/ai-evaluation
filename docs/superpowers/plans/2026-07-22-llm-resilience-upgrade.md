---
change: llm-resilience-upgrade
design-doc: docs/superpowers/specs/2026-07-22-llm-resilience-upgrade-design.md
base-ref: 5f9a72de80c93bf4d8f2af08dc5041fd9e71e04c
---

# AI 可靠性工程 — 实施计划

**Goal:** 升级 ResilientLlmClient 为工程化可靠性客户端。

### Task 1: LlmProperties 重构
- [x] 1.1 新增 ModelConfig 内部类（provider/baseUrl/apiKey/model/temperature）
- [x] 1.2 LlmProperties 新增 primary + fallbacks + retry/circuit/tokenBudget 配置项
- [x] 1.3 编译验证

### Task 2: LlmConfig 重构
- [x] 2.1 resilientLlmClient Bean 从 primary/fallbacks 数组创建客户端
- [x] 2.2 application.yml 重构为 primary + fallbacks[glm-4.5-air] 配置

### Task 3: ResilientLlmClient 增强
- [x] 3.1 半开探测: halfOpen + probeInProgress AtomicBoolean
- [x] 3.2 tokenBudget: checkTokenBudget() 方法
- [x] 3.3 degradationLevel 追踪 + getLastDegradationLevel()
- [x] 3.4 getStatus() 增强: models/degradation/tokenBudget

### Task 4: DB + Entity
- [x] 4.1 V011__degradation_level.sql DDL
- [x] 4.2 EvalAiExperiment 新增 degradationLevel 字段

### Task 5: LlmScoringStrategy 适配
- [x] 5.1 recordExperiment() 写入 degradationLevel
- [x] 5.2 降级时返回 DEGRADED 标记

### Task 6: ResilienceController API
- [x] 6.1 GET /api/v1/ai/resilience-status

### Task 7: 测试
- [x] 7.1 ResilientLlmClientTest: 熔断计数 + 半开状态机
- [x] 7.2 集成验证: 真实 GLM key → L1 fallback
