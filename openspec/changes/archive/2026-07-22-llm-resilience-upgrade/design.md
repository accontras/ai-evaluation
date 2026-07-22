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
    "fallbacks": [
      { "model": "glm-4-flash", "calls": 5, "errors": 0 }
    ]
  },
  "degradation": { "l1_fallback": 5, "l2_rule": 1, "l3_default": 0 },
  "tokenBudget": { "limit": 8000, "breaches": 0 }
}
```

## Risks / Trade-offs

- **[风险] GLM API key 泄露**：key 在 application.yml 中明文 → **缓解**：用 `${GLM_API_KEY}` 环境变量，不硬编码
- **[取舍] 半开探测只有 1 次机会**：可能因网络抖动误判 → 生产环境可调为 2-3 次，当前 1 次够验证流程
