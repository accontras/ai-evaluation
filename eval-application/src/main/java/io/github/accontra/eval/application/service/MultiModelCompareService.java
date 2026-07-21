package io.github.accontra.eval.application.service;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import io.github.accontra.eval.infrastructure.mapper.EvalAiExperimentMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalIndicatorLogMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalModelStandardMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 多模型对比服务 — A1.1。
 *
 * 同一组指标，主模型 (DeepSeek) + 备选模型 (GLM / Qwen) 分别打分，对比一致性。
 * 当前各模型共用同一 API key (见 LlmConfig)，接入真实 key 后无需改代码。
 */
@Component
public class MultiModelCompareService {

    private static final Logger log = LoggerFactory.getLogger(MultiModelCompareService.class);

    private final LlmScoringStrategy primaryScoring;
    private final LlmScoringStrategy glmScoring;
    private final LlmScoringStrategy qwenScoring;

    public MultiModelCompareService(LlmScoringStrategy primaryScoring,
                                     @Qualifier("glm") LlmClient glmClient,
                                     @Qualifier("qwen") LlmClient qwenClient,
                                     EvalModelStandardMapper standardMapper,
                                     EvalIndicatorLogMapper indicatorLogMapper,
                                     EvalAiExperimentMapper experimentMapper) {
        this.primaryScoring = primaryScoring;
        this.glmScoring = new LlmScoringStrategy(glmClient, standardMapper, indicatorLogMapper, experimentMapper, null, null);
        this.qwenScoring = new LlmScoringStrategy(qwenClient, standardMapper, indicatorLogMapper, experimentMapper, null, null);
    }

    /**
     * 三模型打分对比。
     * @return modelScores + per-index variances
     */
    public CompareResult compare(EvaluationContext ctx) {
        Map<String, Map<String, BigDecimal>> allScores = new LinkedHashMap<>();

        // DeepSeek (主模型)
        var primary = primaryScoring.scoreAll(ctx);
        Map<String, BigDecimal> primaryMap = new LinkedHashMap<>();
        primary.forEach((k, v) -> primaryMap.put(k, v.score()));
        allScores.put("deepseek", primaryMap);

        // GLM (备选)
        try {
            var glm = glmScoring.scoreAll(ctx);
            Map<String, BigDecimal> glmMap = new LinkedHashMap<>();
            glm.forEach((k, v) -> glmMap.put(k, v.score()));
            allScores.put("glm", glmMap);
        } catch (Exception e) {
            log.error("[MultiModel] GLM 失败: {}", e.getMessage());
            allScores.put("glm", Map.of());
        }

        // Qwen (备选)
        try {
            var qwen = qwenScoring.scoreAll(ctx);
            Map<String, BigDecimal> qwenMap = new LinkedHashMap<>();
            qwen.forEach((k, v) -> qwenMap.put(k, v.score()));
            allScores.put("qwen", qwenMap);
        } catch (Exception e) {
            log.error("[MultiModel] Qwen 失败: {}", e.getMessage());
            allScores.put("qwen", Map.of());
        }

        // 指标级标准差
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

        log.info("[MultiModel] 对比完成: {}指标, deepseek/glm/qwen", primaryMap.size());
        return new CompareResult(allScores, stdDevs);
    }

    public record CompareResult(Map<String, Map<String, BigDecimal>> modelScores,
                                 Map<String, Double> stdDevs) {}
}
