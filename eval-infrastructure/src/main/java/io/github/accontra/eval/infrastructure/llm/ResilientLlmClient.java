package io.github.accontra.eval.infrastructure.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 可靠性客户端 — A4。
 *
 * 重试 + 多模型 Fallback 链 + 熔断 + 降级。
 * 封装主 LlmClient，对调用方透明。
 */
public class ResilientLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientLlmClient.class);

    private final LlmClient primary;
    private final List<LlmClient> fallbacks;
    private final int maxRetries;
    private final int circuitThreshold;

    // 熔断状态
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile boolean circuitOpen = false;
    private volatile long circuitOpenedAt = 0;
    private static final long CIRCUIT_HALF_OPEN_MS = 30_000; // 30s后半开

    // 统计
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger fallbackCount = new AtomicInteger(0);
    private final AtomicInteger degradeCount = new AtomicInteger(0);
    private final AtomicInteger circuitTripCount = new AtomicInteger(0);

    public ResilientLlmClient(LlmClient primary, List<LlmClient> fallbacks,
                                int maxRetries, int circuitThreshold) {
        this.primary = primary;
        this.fallbacks = fallbacks;
        this.maxRetries = maxRetries;
        this.circuitThreshold = circuitThreshold;
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
        // 1. 熔断检查
        if (circuitOpen) {
            if (System.currentTimeMillis() - circuitOpenedAt > CIRCUIT_HALF_OPEN_MS) {
                log.info("[Resilience] Circuit half-open, probing primary...");
                circuitOpen = false;
            } else {
                log.warn("[Resilience] Circuit OPEN, skip primary → fallback");
                return tryFallbacks(sp, up, isJson);
            }
        }

        // 2. 主模型 + 重试
        LlmResponse resp = tryWithRetry(primary, sp, up, maxRetries);
        if (!resp.isError()) {
            consecutiveFailures.set(0);
            return resp;
        }

        // 3. 主模型失败 → 计数 + 熔断判定
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= circuitThreshold) {
            circuitOpen = true;
            circuitOpenedAt = System.currentTimeMillis();
            circuitTripCount.incrementAndGet();
            log.warn("[Resilience] Circuit TRIPPED after {} consecutive failures", failures);
        }

        // 4. Fallback 链
        return tryFallbacks(sp, up, isJson);
    }

    private LlmResponse tryWithRetry(LlmClient client, String sp, String up, int retries) {
        LlmResponse lastResp = null;
        for (int i = 0; i <= retries; i++) {
            lastResp = client.chat(sp, up);
            if (!lastResp.isError()) return lastResp;
            if (i < retries) {
                log.warn("[Resilience] Retry {}/{} after error: {}", i + 1, retries, lastResp.errorType());
                try { Thread.sleep(500L * (i + 1)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
        return lastResp;
    }

    private LlmResponse tryFallbacks(String sp, String up, boolean isJson) {
        for (int i = 0; i < fallbacks.size(); i++) {
            var fb = fallbacks.get(i);
            log.info("[Resilience] Trying fallback {}/{}: {}", i + 1, fallbacks.size(), fb.getModel());
            var resp = fb.chat(sp, up);
            if (!resp.isError()) {
                fallbackCount.incrementAndGet();
                return resp;
            }
        }
        // Level 3: 彻底降级
        degradeCount.incrementAndGet();
        log.error("[Resilience] ALL models failed, degraded");
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

    // ---- 状态查询 ----

    public Map<String, Object> getStatus() {
        return Map.of(
                "circuitOpen", circuitOpen,
                "consecutiveFailures", consecutiveFailures.get(),
                "circuitThreshold", circuitThreshold,
                "totalCalls", totalCalls.get(),
                "fallbackCount", fallbackCount.get(),
                "degradeCount", degradeCount.get(),
                "circuitTripCount", circuitTripCount.get(),
                "primaryModel", primary.getModel(),
                "fallbackModels", fallbacks.stream().map(LlmClient::getModel).toList()
        );
    }

    @Override public String getModel() { return primary.getModel(); }
    @Override public double getTemperature() { return primary.getTemperature(); }
}
