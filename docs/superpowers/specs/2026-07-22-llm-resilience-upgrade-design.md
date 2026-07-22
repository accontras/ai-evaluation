---
comet_change: llm-resilience-upgrade
role: technical-design
canonical_spec: openspec
---

# AI 可靠性工程 — 技术设计

> A4：ResilientLlmClient 系统化升级为真正的 AI 可靠性工程。

## 1. 架构概览

```
application.yml                     ResilientLlmClient
  llm:                                  ┌──────────────────────┐
    primary:                            │ executeWithResilience │
      model: deepseek-chat              │                      │
      api-key: ${DEEPSEEK_KEY}          │ 1. checkTokenBudget  │
    fallbacks:                          │ 2. circuit check     │
      - model: glm-4.5-air              │ 3. half-open probe   │
        api-key: ${GLM_API_KEY}         │ 4. primary + retry   │
                                        │ 5. fallback chain    │
            │                           │ 6. degradation track │
            ▼                           └──────────┬───────────┘
       LlmConfig                                    │
         creates                                   ▼
    ResilientLlmClient                   LlmScoringStrategy
      from properties                    .recordExperiment()
         │                               reads degradationLevel
         ▼
   ResilienceController ── GET /api/v1/ai/resilience-status
```

## 2. 配置模型

```yaml
llm:
  primary:
    provider: deepseek
    base-url: https://api.deepseek.com
    api-key: ${DEEPSEEK_API_KEY:sk-0e7c2ba3e6ac4ef1867deddb70494bf8}
    model: deepseek-chat
    temperature: 0.3
  fallbacks:
    - provider: glm
      base-url: https://open.bigmodel.cn/api/paas/v4
      api-key: ${GLM_API_KEY}
      model: glm-4.5-air
      temperature: 0.3
  retry:
    max-retries: 1
  circuit:
    threshold: 5
    half-open-ms: 30000
  token-budget:
    max-per-eval: 8000
```

`LlmProperties`：
```java
public static class ModelConfig {
    String provider, baseUrl, apiKey, model;
    double temperature = 0.3;
}
// primary + fallbacks + retry/circuit/tokenBudget
```

## 3. ResilientLlmClient 增强

### 3.1 半开探测

```java
private final AtomicBoolean halfOpen = new AtomicBoolean(false);
private final AtomicBoolean probeInProgress = new AtomicBoolean(false);

// 在 executeWithResilience() 中:
if (circuitOpen) {
    long elapsed = System.currentTimeMillis() - circuitOpenedAt;
    if (elapsed > CIRCUIT_HALF_OPEN_MS) {
        halfOpen.set(true);
        // CAS 抢探测权
        if (probeInProgress.compareAndSet(false, true)) {
            // 允许 1 次请求
        } else {
            return tryFallbacks(...); // 别人在探测，我走 fallback
        }
    }
}
// 探测成功: circuitOpen=false, halfOpen=false, probeInProgress=false, failures=0
// 探测失败: circuitOpen=true, halfOpen=false, probeInProgress=false, 重置 circuitOpenedAt
```

### 3.2 Token 预算

```java
private int evalTokenUsed = 0;

private boolean checkTokenBudget(int newTokens) {
    evalTokenUsed += newTokens;
    return evalTokenUsed <= tokenBudgetMax;
}
// 每次 chat 后累加，超限返回 ALL_MODELS_FAILED
```

### 3.3 降级追踪

```java
private volatile String lastDegradationLevel = "NONE";

// L1: tryFallbacks 中找到可用模型 → L1_FALLBACK
// L2: 全部失败 + 调用方降级到规则引擎 → L2_RULE (由 LlmScoringStrategy 设置)
// L3: 规则引擎也不可用 → L3_DEFAULT
```

## 4. LlmScoringStrategy 适配

```java
// recordExperiment() 新增:
if (llmClient instanceof ResilientLlmClient r) {
    exp.setDegradationLevel(r.getLastDegradationLevel());
}

// degradedScores() 中增加 L2/L3 标记
```

## 5. ResilienceController

```java
@RestController
@RequestMapping("/api/v1/ai")
public class ResilienceController {
    private final ResilientLlmClient resilientClient;

    @GetMapping("/resilience-status")
    public Result<Map<String, Object>> status() {
        return Result.ok(resilientClient.getStatus());
    }
}
```

## 6. 文件变更清单

| 操作 | 文件 |
|------|------|
| 修改 | `LlmProperties.java` |
| 修改 | `LlmConfig.java` |
| 修改 | `ResilientLlmClient.java` |
| 修改 | `LlmScoringStrategy.java` |
| 修改 | `EvalAiExperiment.java` |
| 新增 | `ResilienceController.java` |
| 修改 | `application.yml` |
| 新增 | `docs/sql/V011__degradation_level.sql` |
| 新增 | `ResilientLlmClientTest.java` |
