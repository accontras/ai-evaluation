# llm-resilience Specification

## Purpose
TBD - created by archiving change llm-resilience-upgrade. Update Purpose after archive.
## Requirements
### Requirement: 多模型 fallback 链配置
系统 SHALL 支持通过 `application.yml` 的 `llm.primary` 和 `llm.fallbacks` 数组独立配置每个模型的 provider/baseUrl/apiKey/model。

#### Scenario: 正常加载配置
- **WHEN** 应用启动且 `application.yml` 配置了 1 个 primary + 2 个 fallbacks
- **THEN** `ResilientLlmClient.getStatus()` 返回正确的 primaryModel 和 fallbackModels 列表

#### Scenario: 无 fallback 配置
- **WHEN** `llm.fallbacks` 为空或未配置
- **THEN** 系统正常运行，仅使用 primary 模型

### Requirement: 熔断半开探测
系统 SHALL 在熔断打开 30 秒后进入半开状态，允许 1 次探测请求通过。探测成功则关闭熔断，失败则重新熔断并重置计时器。

#### Scenario: 半开探测成功
- **WHEN** 熔断打开超过 30 秒，下一次请求自动进入半开探测
- **THEN** 该探测请求成功 → 熔断关闭，`consecutiveFailures` 归零

#### Scenario: 半开探测失败
- **WHEN** 半开探测请求失败
- **THEN** 熔断重新打开，计时器重置，后续请求走 fallback

### Requirement: Token 预算保护
系统 SHALL 支持可配置的单次评估 token 消耗上限，超过阈值时跳过 LLM 调用直接降级。

#### Scenario: 预算内调用
- **WHEN** 单次评估累计 token < `token-budget.max-per-eval`
- **THEN** 正常调用 LLM

#### Scenario: 超预算降级
- **WHEN** 单次评估 input tokens > `token-budget.max-per-eval`
- **THEN** 跳过 LLM 调用，记录 degradation_level=L3_DEFAULT

### Requirement: 降级分层追踪
系统 SHALL 按 L1（切备选模型）→ L2（规则引擎代替）→ L3（默认 70 分）分层降级，并在 `eval_ai_experiment.degradation_level` 字段记录降级层级。

#### Scenario: L1 fallback 成功
- **WHEN** 主模型失败, fallback 模型成功返回
- **THEN** `degradation_level` = L1_FALLBACK

#### Scenario: L2 规则引擎降级
- **WHEN** 所有 LLM 模型失败, 规则引擎可正常计算
- **THEN** `degradation_level` = L2_RULE

#### Scenario: L3 默认分降级
- **WHEN** LLM 和规则引擎均不可用
- **THEN** `degradation_level` = L3_DEFAULT, 得分 = 70, 标记 DEGRADED

### Requirement: 可视化状态 API
系统 SHALL 提供 `GET /api/v1/ai/resilience-status` 端点，返回熔断状态、各模型调用统计、降级分布和 token 预算信息。

#### Scenario: 查询状态
- **WHEN** 客户端请求 `/api/v1/ai/resilience-status`
- **THEN** 返回 JSON，包含 circuit/models/degradation/tokenBudget 四部分

