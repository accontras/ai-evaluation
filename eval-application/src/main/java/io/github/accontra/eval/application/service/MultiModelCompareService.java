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
        Map<String, BigDecimal> primaryMap = new LinkedHashMap<>();
        primary.forEach((k, v) -> primaryMap.put(k, v.score()));

        Map<String, Map<String, BigDecimal>> allScores = new LinkedHashMap<>();
        allScores.put("deepseek", primaryMap);

        log.info("[MultiModel] 对比完成: {}指标", primaryMap.size());
        return new CompareResult(allScores, Map.of());
    }

    public record CompareResult(Map<String, Map<String, BigDecimal>> modelScores,
                                 Map<String, Double> stdDevs) {}
}
