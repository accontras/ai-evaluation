package io.github.accontra.eval.api.request;

import java.util.Map;

/**
 * 评估执行请求 — 路径A（data 直传）。
 */
public record ExecuteEvaluationRequest(
        String sceneCode,
        String bizId,
        String dataPeriod,
        Map<String, Object> data
) {}
