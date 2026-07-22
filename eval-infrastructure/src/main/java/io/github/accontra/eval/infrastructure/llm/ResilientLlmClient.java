package io.github.accontra.eval.infrastructure.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 可靠性客户端 — A4。
 *
 * 重试 + 多模型 Fallback 链 + 熔断(真半开探测) + token 预算 + 降级追踪。
 */
public class ResilientLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientLlmClient.class);

    private final LlmClient primary;
    private final List<LlmClient> fallbacks;
    private final int maxRetries;
    private final int circuitThreshold;
    private final long circuitHalfOpenMs;
    private final int tokenBudgetMax;

    // 熔断状态
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile boolean circuitOpen = false;
    private volatile long circuitOpenedAt = 0;
    private final AtomicBoolean halfOpen = new AtomicBoolean(false);
    private final AtomicBoolean probeInProgress = new AtomicBoolean(false);

    // Token 预算
    private int evalTokenUsed = 0;

    // 降级追踪
    private volatile String lastDegradationLevel = "NONE";

    // 统计
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger primaryErrors = new AtomicInteger(0);
    private final AtomicInteger fallbackCount = new AtomicInteger(0);
    private final AtomicInteger fallbackErrors = new AtomicInteger(0);
    private final AtomicInteger degradeCount = new AtomicInteger(0);
    private final AtomicInteger circuitTripCount = new AtomicInteger(0);
    private final AtomicInteger tokenBreachCount = new AtomicInteger(0);

    public ResilientLlmClient(LlmClient primary, List<LlmClient> fallbacks,
                               int maxRetries, int circuitThreshold,
                               long circuitHalfOpenMs, int tokenBudgetMax) {
        this.primary = primary;
        this.fallbacks = fallbacks;
        this.maxRetries = maxRetries;
        this.circuitThreshold = circuitThreshold;
        this.circuitHalfOpenMs = circuitHalfOpenMs;
        this.tokenBudgetMax = tokenBudgetMax;
    }

    @Override
    public LlmResponse chat(String systemPrompt, String userPrompt) {
        totalCalls.incrementAndGet();
        return executeWithResilience(systemPrompt, userPrompt, false);
    }

    @Override
    public LlmJsonResponse chatForJson(String systemPrompt, String userPrompt) {
        totalCalls.incrementAndGet();
        LlmResponse resp = executeWithResilience(systemPrompt, userPrompt, true);
        if (resp.isError()) return new LlmJsonResponse(null, resp);
        return extractJson(resp);
    }

    /** 核心韧性逻辑 */
    private LlmResponse executeWithResilience(String sp, String up, boolean isJson) {
        // 0. Token 预算检查
        if (evalTokenUsed >= tokenBudgetMax) {
            tokenBreachCount.incrementAndGet();
            lastDegradationLevel = "L3_DEFAULT";
            log.warn("[Resilience] Token budget exceeded ({}/{}), degraded to L3",
                    evalTokenUsed, tokenBudgetMax);
            return new LlmResponse(null, 0, 0, 0, "TOKEN_BUDGET_EXCEEDED");
        }

        // 1. 熔断检查
        if (circuitOpen) {
            long elapsed = System.currentTimeMillis() - circuitOpenedAt;
            if (elapsed > circuitHalfOpenMs) {
                halfOpen.set(true);
                if (probeInProgress.compareAndSet(false, true)) {
                    log.info("[Resilience] Half-open probe starting...");
                    // 允许探测请求通过
                } else {
                    log.info("[Resilience] Half-open probe already in progress, using fallback");
                    return tryFallbacks(sp, up);
                }
            } else {
                log.warn("[Resilience] Circuit OPEN ({}ms remaining), skip primary → fallback",
                        circuitHalfOpenMs - elapsed);
                return tryFallbacks(sp, up);
            }
        }

        // 2. 主模型 + 重试
        LlmResponse resp = tryWithRetry(primary, sp, up, maxRetries);
        evalTokenUsed += resp.totalTokens();

        if (!resp.isError()) {
            consecutiveFailures.set(0);
            // 半开探测成功 → 关闭熔断
            if (probeInProgress.get()) {
                circuitOpen = false;
                halfOpen.set(false);
                probeInProgress.set(false);
                consecutiveFailures.set(0);
                log.info("[Resilience] Half-open probe SUCCESS, circuit closed");
            }
            lastDegradationLevel = "NONE";
            return resp;
        }

        // 半开探测失败
        if (probeInProgress.get()) {
            probeInProgress.set(false);
            halfOpen.set(false);
            circuitOpenedAt = System.currentTimeMillis();
            log.warn("[Resilience] Half-open probe FAILED, circuit re-opened");
            return tryFallbacks(sp, up);
        }

        // 3. 主模型失败 → 计数 + 熔断判定
        primaryErrors.incrementAndGet();
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= circuitThreshold) {
            circuitOpen = true;
            circuitOpenedAt = System.currentTimeMillis();
            circuitTripCount.incrementAndGet();
            log.warn("[Resilience] Circuit TRIPPED after {} consecutive failures", failures);
        }

        // 4. Fallback 链
        return tryFallbacks(sp, up);
    }

    private LlmResponse tryWithRetry(LlmClient client, String sp, String up, int retries) {
        LlmResponse lastResp = null;
        for (int i = 0; i <= retries; i++) {
            lastResp = client.chat(sp, up);
            if (!lastResp.isError()) return lastResp;
            if (i < retries) {
                log.warn("[Resilience] Retry {}/{} after error: {}", i + 1, retries, lastResp.errorType());
                try { Thread.sleep(500L * (i + 1)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
        return lastResp;
    }

    private LlmResponse tryFallbacks(String sp, String up) {
        for (int i = 0; i < fallbacks.size(); i++) {
            var fb = fallbacks.get(i);
            log.info("[Resilience] Trying fallback {}/{}: {}", i + 1, fallbacks.size(), fb.getModel());
            var resp = fb.chat(sp, up);
            evalTokenUsed += resp.totalTokens();
            if (!resp.isError()) {
                fallbackCount.incrementAndGet();
                lastDegradationLevel = "L1_FALLBACK";
                return resp;
            }
            fallbackErrors.incrementAndGet();
        }
        // 所有模型失败
        degradeCount.incrementAndGet();
        lastDegradationLevel = "L2_RULE";
        log.error("[Resilience] ALL models failed, signal L2_RULE degradation");
        return new LlmResponse(null, 0, 0, 0, "ALL_MODELS_FAILED");
    }

    private LlmJsonResponse extractJson(LlmResponse resp) {
        String raw = resp.content();
        if (raw == null) return new LlmJsonResponse(null, resp);
        String jsonStr = raw;
        if (raw.contains("```json")) jsonStr = raw.substring(raw.indexOf("```json") + 7, raw.lastIndexOf("```"));
        else if (raw.contains("```")) jsonStr = raw.substring(raw.indexOf("```") + 3, raw.lastIndexOf("```"));
        try {
            return new LlmJsonResponse(cn.hutool.json.JSONUtil.parseObj(jsonStr.trim()), resp);
        } catch (Exception e) {
            return new LlmJsonResponse(null, resp);
        }
    }

    // ---- 公共 API ----

    public String getLastDegradationLevel() { return lastDegradationLevel; }

    public void setLastDegradationLevel(String level) { this.lastDegradationLevel = level; }

    public void resetTokenBudget() { evalTokenUsed = 0; }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        status.put("circuit", Map.of(
                "open", circuitOpen,
                "halfOpen", halfOpen.get(),
                "consecutiveFailures", consecutiveFailures.get(),
                "threshold", circuitThreshold
        ));

        Map<String, Object> primaryStats = new LinkedHashMap<>();
        primaryStats.put("model", primary.getModel());
        primaryStats.put("calls", totalCalls.get());
        primaryStats.put("errors", primaryErrors.get());
        status.put("primary", primaryStats);

        List<Map<String, Object>> fbList = new ArrayList<>();
        for (var fb : fallbacks) {
            fbList.add(Map.of("model", fb.getModel()));
        }
        status.put("fallbacks", fbList);

        status.put("degradation", Map.of(
                "current", lastDegradationLevel,
                "l1_fallback", fallbackCount.get(),
                "l2_rule", degradeCount.get(),
                "circuitTrips", circuitTripCount.get()
        ));

        status.put("tokenBudget", Map.of(
                "limit", tokenBudgetMax,
                "used", evalTokenUsed,
                "breaches", tokenBreachCount.get()
        ));

        status.put("totals", Map.of(
                "calls", totalCalls.get(),
                "fallbackErrors", fallbackErrors.get()
        ));

        return status;
    }

    @Override public String getModel() { return primary.getModel(); }
    @Override public double getTemperature() { return primary.getTemperature(); }
}
