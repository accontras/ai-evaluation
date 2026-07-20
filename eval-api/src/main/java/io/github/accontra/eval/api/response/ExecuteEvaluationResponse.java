package io.github.accontra.eval.api.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 评估执行响应 — 包含总分 + 逐指标明细。
 */
public record ExecuteEvaluationResponse(
        String bizId,
        String sceneCode,
        BigDecimal totalScore,
        String riskLevel,
        String grade,
        String scoringMode,
        List<IndicatorResult> indicators
) {
    public record IndicatorResult(
            String indexCode,
            String indexName,
            BigDecimal score,
            String reason
    ) {}
}
