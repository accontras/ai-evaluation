package io.github.accontra.eval.application.service;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import io.github.accontra.eval.infrastructure.mapper.EvalAiExperimentMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalIndicatorLogMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalModelStandardMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 多模型对比服务 — A1.1 (简化)。
 *
 * A4 重构后模型通过 ResilientLlmClient 统一管理，
 * 多模型对比改为通过 fallback 链中不同模型来对比。
 */
@Component
public class MultiModelCompareService {

    private static final Logger log = LoggerFactory.getLogger(MultiModelCompareService.class);

    private final LlmScoringStrategy primaryScoring;

    public MultiModelCompareService(LlmScoringStrategy primaryScoring) {
        this.primaryScoring = primaryScoring;
    }

    /**
     * 三模型打分对比。
     * @return modelScores + per-index variances
     */
    public CompareResult compare(EvaluationContext ctx) {
        var primary = primaryScoring.scoreAll(ctx);

        Map<String, IndicatorStats> indicators = new LinkedHashMap<>();
        primary.forEach((k, v) -> indicators.put(k, new IndicatorStats(
                k, v.score(), null, new BigDecimal[]{v.score()}, null)));

        Map<String, ModelScoreSet> modelScores = new LinkedHashMap<>();
        modelScores.put("deepseek", new ModelScoreSet(indicators, "deepseek"));

        log.info("[MultiModel] 对比完成: {}指标", indicators.size());
        return new CompareResult(modelScores, Map.of(), Map.of(), Map.of(), 1, 1);
    }

    public record CompareResult(
        Map<String, ModelScoreSet> modelScores,
        Map<String, Double> ruleBaseline,
        Map<String, Double> crossModelVariance,
        Map<String, String> errors,
        int availableModelCount,
        int totalModelCount
    ) {}

    public record ModelScoreSet(
        Map<String, IndicatorStats> indicators,
        String model
    ) {}

    public record IndicatorStats(
        String indexCode,
        BigDecimal meanScore,
        Double stdDev,
        BigDecimal[] rawScores,
        Double deviationFromRule
    ) {}
}
