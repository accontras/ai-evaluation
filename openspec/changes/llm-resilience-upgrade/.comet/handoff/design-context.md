# Comet Design Handoff

- Change: llm-resilience-upgrade
- Phase: design
- Mode: compact
- Context hash: 0b79a58637a882ea74b81cc5277ef066ddb62f5973dbc1df21efdc1a7d9d7bd6

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/llm-resilience-upgrade/proposal.md

- Source: openspec/changes/llm-resilience-upgrade/proposal.md
- Lines: 1-26
- SHA256: dca2e83fac22d2599af33fdef08abef7e93065a6b60b82a8c2d70e2d55ef3d68

```md
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
```

## openspec/changes/llm-resilience-upgrade/design.md

- Source: openspec/changes/llm-resilience-upgrade/design.md
- Lines: 1-93
- SHA256: 04c2564883216f52f930c2631e0ae8aec8b2d64c75f35a667a239cc988c3388f

[TRUNCATED]

```md
## Context

`ResilientLlmClient` 和 `AiCallGuard`（熔断+重试+fallback骨架）已在 S24 完成。当前缺陷：
- `LlmConfig` 三个 fallback Bean 用同样的 baseUrl/apiKey，实际没切换模型
- 半开探测是简单 boolean 翻转，非标准的"允许 1 次探测请求"
- 无 token 预算保护
- 降级不可观测

## Goals / Non-Goals

**Goals:**
- primary/fallbacks 独立配置，真正多模型切换
- 真半开探测、token 预算保护
- degradation_level 追踪（L1→L2→L3）
- 可视化 API

**Non-Goals:**
- 不改 LlmClient 接口
- 不引入 Resilience4j/Hystrix
- 不改评分流水线

## Decisions

### D1: 配置结构 — primary + fallbacks 数组

```yaml
llm:
  primary:
    provider: deepseek
    base-url: https://api.deepseek.com
    api-key: ${DEEPSEEK_KEY}
    model: deepseek-chat
  fallbacks:
    - provider: glm
      base-url: https://open.bigmodel.cn/api/paas/v4
      api-key: ${GLM_KEY}
      model: glm-4-flash
    - provider: qwen
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${QWEN_KEY}
      model: qwen-turbo
  retry:
    max-retries: 1
  circuit:
    threshold: 5
    half-open-ms: 30000
  token-budget:
    max-per-eval: 8000
```

**理由**：primary/fallbacks 语义清晰，每个模型独立配置。retry/circuit/token-budget 分组，可读性好。

### D2: 半开探测 — 允许 1 次探测

当前实现：`circuitOpen = false` 直接恢复。改为：
1. 熔断打开 → 30s 后进入 half-open
2. Half-open → 允许 1 次请求通过
3. 成功 → 关闭熔断；失败 → 重新熔断，重置计时器

### D3: Token 预算保护

在 `executeWithResilience()` 入口检查：累计 input+output tokens 若超过 `tokenBudget.maxPerEval`，跳过 LLM 调用，直接走降级。预算检查在 `ResilientLlmClient` 层做——不在调用方（LlmScoringStrategy），保持透明。

### D4: degradation_level 字段

```sql
ALTER TABLE eval_ai_experiment ADD COLUMN degradation_level VARCHAR(16)
  DEFAULT 'NONE' COMMENT '降级层级: NONE/L1_FALLBACK/L2_RULE/L3_DEFAULT';
```

LlmScoringStrategy.recordExperiment() 读取 `ResilientLlmClient.getLastDegradationLevel()` 写入。

### D5: 可视化 API

`GET /api/v1/ai/resilience-status` → 返回：
```json
{
  "circuit": { "open": false, "halfOpen": false, "consecutiveFailures": 2, "threshold": 5 },
  "models": {
    "primary": { "model": "deepseek-chat", "calls": 150, "errors": 3 },
```

Full source: openspec/changes/llm-resilience-upgrade/design.md

## openspec/changes/llm-resilience-upgrade/tasks.md

- Source: openspec/changes/llm-resilience-upgrade/tasks.md
- Lines: 1-39
- SHA256: a4f2bd5438f7ef1191cc6c1c57de0024becd39cee8d508b13d96f25006b7160c

```md
## 1. LlmProperties 重构

- [ ] 1.1 新增 `ModelConfig` 内部类（provider/baseUrl/apiKey/model/temperature）
- [ ] 1.2 `LlmProperties` 新增 primary (ModelConfig) + fallbacks (List<ModelConfig>) + retry/circuit/tokenBudget 配置项
- [ ] 1.3 保留旧字段兼容（@Deprecated），映射到 primary

## 2. LlmConfig 重构

- [ ] 2.1 `resilientLlmClient` Bean 从 `LlmProperties` 的 primary/fallbacks 数组创建客户端
- [ ] 2.2 每个 fallback 使用独立的 baseUrl/apiKey/model（不再复用 primary 配置）
- [ ] 2.3 application.yml 添加 GLM fallback 配置（glm-4-flash + 真实 key）

## 3. ResilientLlmClient 增强

- [ ] 3.1 半开状态原子变量 + 真探测逻辑（halfOpen + probeInProgress）
- [ ] 3.2 Token 预算保护：`checkTokenBudget()` 方法
- [ ] 3.3 `getLastDegradationLevel()` 方法 + `degradationLevel` 追踪
- [ ] 3.4 `getStatus()` 增强：返回 models 统计 + degradation 分布 + tokenBudget

## 4. DB + Entity 更新

- [ ] 4.1 `ALTER TABLE eval_ai_experiment ADD COLUMN degradation_level`
- [ ] 4.2 `EvalAiExperiment` Entity 新增 `degradationLevel` 字段 + getter/setter

## 5. LlmScoringStrategy 适配

- [ ] 5.1 `recordExperiment()` 写入 `degradationLevel`
- [ ] 5.2 降级 L2/L3 时返回 DEGRADED 标记

## 6. 可视化 API

- [ ] 6.1 创建 `ResilienceController`：`GET /api/v1/ai/resilience-status`
- [ ] 6.2 返回 circuit/models/degradation/tokenBudget JSON

## 7. 测试验证

- [ ] 7.1 单元测试：`RagQualityServiceTest` 风格，测熔断计数 + 半开逻辑
- [ ] 7.2 集成测试：配置真实 GLM key → 跑一次评估 → 检查 degradation_level
- [ ] 7.3 手动验证：resilience-status API 返回正确数据
```

## openspec/changes/llm-resilience-upgrade/specs/llm-resilience/spec.md

- Source: openspec/changes/llm-resilience-upgrade/specs/llm-resilience/spec.md
- Lines: 1-56
- SHA256: af5e27039dbe98a99a7ca0d21e70bed4a06f74120e05e01239c8640d3847d3c0

```md
## ADDED Requirements

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
```

