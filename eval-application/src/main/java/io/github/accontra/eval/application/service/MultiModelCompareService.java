package io.github.accontra.eval.application.service;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 多模型对比服务 — S31。
 *
 * 同一组指标, 主模型 (t=0.3) + 备选模型 (t=0.1) 分别打分, 对比一致性。
 * 扩展: 接入第三方模型后只需加 LlmClient Bean + 一行配置。
 */
@Component
public class MultiModelCompareService {

    private static final Logger log = LoggerFactory.getLogger(MultiModelCompareService.class);

    private final LlmScoringStrategy primaryScoring;
    private final LlmScoringStrategy altScoring;

    public MultiModelCompareService(LlmScoringStrategy primaryScoring,
                                     @Qualifier("llmClientLowTemp") LlmClient altClient,
                                     io.github.accontra.eval.infrastructure.mapper.EvalModelStandardMapper standardMapper,
                                     io.github.accontra.eval.infrastructure.mapper.EvalIndicatorLogMapper indicatorLogMapper,
                                     io.github.accontra.eval.infrastructure.mapper.EvalAiExperimentMapper experimentMapper) {
        this.primaryScoring = primaryScoring;
        this.altScoring = new LlmScoringStrategy(altClient, standardMapper, indicatorLogMapper, experimentMapper, null);
    }

    /**
     * 双模型打分对比。
     * @return modelScores + per-index variances
     */
    public CompareResult compare(EvaluationContext ctx) {
        Map<String, Map<String, BigDecimal>> allScores = new LinkedHashMap<>();

        // 主模型 t=0.3
        var primary = primaryScoring.scoreAll(ctx);
        Map<String, BigDecimal> primaryMap = new LinkedHashMap<>();
        primary.forEach((k, v) -> primaryMap.put(k, v.score()));
        allScores.put("t=0.3", primaryMap);

        // 备选模型 t=0.1
        try {
            var alt = altScoring.scoreAll(ctx);
            Map<String, BigDecimal> altMap = new LinkedHashMap<>();
            alt.forEach((k, v) -> altMap.put(k, v.score()));
            allScores.put("t=0.1", altMap);
        } catch (Exception e) {
            log.error("[MultiModel] alt模型失败: {}", e.getMessage());
            allScores.put("t=0.1", Map.of());
        }

        // 指标级方差
        Map<String, Double> stdDevs = new LinkedHashMap<>();
        for (var code : primaryMap.keySet()) {
            var vals = allScores.values().stream()
                    .filter(m -> m.containsKey(code))
                    .map(m -> m.get(code).doubleValue())
                    .toList();
            if (vals.size() < 2) continue;
            double mean = vals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double var = vals.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
            stdDevs.put(code, Math.round(Math.sqrt(var) * 100.0) / 100.0);
        }

        log.info("[MultiModel] 对比完成: {}指标, t=0.3 vs t=0.1", primaryMap.size());
        return new CompareResult(allScores, stdDevs);
    }

    public record CompareResult(Map<String, Map<String, BigDecimal>> modelScores,
                                 Map<String, Double> stdDevs) {}
}
