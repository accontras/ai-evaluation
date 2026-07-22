package io.github.accontra.eval.application.service;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.application.strategy.RuleScoreStrategy;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 多模型对比服务 — 核心对比逻辑 (2.1-2.4)。
 *
 * 职责:
 *   2.1 跨模型并行打分 — 通过 compareClients (primary + fallback) 并行调用
 *   2.2 规则引擎基线 — 提取 RuleScoreStrategy 分数作为 baseline
 *   2.3 稳定性重复 — 每个模型重复 repeatCount 次并计算均值/标准差
 *   2.4 跨模型方差 — 对各指标计算各模型均值间的方差
 *
 * 错误隔离: 单个模型失败不影响其他模型。
 */
@Component
public class MultiModelCompareService {

    private static final Logger log = LoggerFactory.getLogger(MultiModelCompareService.class);
    private static final int DEFAULT_REPEAT_COUNT = 3;
    private static final int TIMEOUT_SECONDS = 120;

    private final Map<String, LlmClient> compareClients;
    private final ScoringStrategyFactory strategyFactory;
    private final RuleScoreStrategy ruleStrategy;

    public MultiModelCompareService(Map<String, LlmClient> compareClients,
                                     ScoringStrategyFactory strategyFactory,
                                     RuleScoreStrategy ruleStrategy) {
        this.compareClients = compareClients;
        this.strategyFactory = strategyFactory;
        this.ruleStrategy = ruleStrategy;
    }

    /**
     * 默认重复 3 次的多模型对比。
     * @param ctx 评估上下文
     * @return 对比结果 (modelScores + ruleBaseline + crossModelVariance + errors)
     */
    public CompareResult compare(EvaluationContext ctx) {
        return compare(ctx, DEFAULT_REPEAT_COUNT);
    }

    /**
     * 多模型对比主逻辑。
     *
     * @param ctx         评估上下文
     * @param repeatCount 每个模型重复打分次数 (稳定性统计)
     * @return 对比结果
     */
    public CompareResult compare(EvaluationContext ctx, int repeatCount) {
        if (compareClients == null || compareClients.isEmpty()) {
            log.warn("[Compare] 无可比模型");
            return new CompareResult(Map.of(), Map.of(), Map.of(), Map.of(), 0, 0);
        }

        // 1. 规则引擎基线
        Map<String, Double> ruleBaseline = extractRuleBaseline(ctx);

        // 2. 跨模型并行打分
        var futures = new ArrayList<CompletableFuture<ModelScoreSet>>();
        var modelNames = new ArrayList<String>();
        for (var entry : compareClients.entrySet()) {
            String name = entry.getKey();
            LlmClient client = entry.getValue();
            modelNames.add(name);
            futures.add(CompletableFuture.supplyAsync(() ->
                scoreModel(name, client, ctx, repeatCount, ruleBaseline)));
        }

        // 3. 收集结果 + 错误隔离
        var modelScores = new LinkedHashMap<String, ModelScoreSet>();
        var errors = new LinkedHashMap<String, String>();
        int successCount = 0;
        for (int i = 0; i < futures.size(); i++) {
            String name = modelNames.get(i);
            try {
                var result = futures.get(i).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                modelScores.put(name, result);
                successCount++;
            } catch (Exception e) {
                log.error("[Compare] 模型 {} 打分失败: {}", name, e.getMessage());
                errors.put(name, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        // 4. 跨模型方差
        var crossModelVariance = computeCrossModelVariance(modelScores);

        log.info("[Compare] 对比完成: models={}/{}, indicators={}",
                successCount, compareClients.size(), crossModelVariance.size());
        return new CompareResult(modelScores, ruleBaseline, crossModelVariance,
                errors, successCount, compareClients.size());
    }

    /**
     * 单模型反复打分 + 稳定性统计。
     *
     * @param name         模型名称 (client key)
     * @param client       LlmClient 实例
     * @param ctx          评估上下文
     * @param repeatCount  重复次数
     * @param ruleBaseline 规则基线 (用于偏差计算)
     * @return 该模型的 ScoreSet (含各指标均值/标准差/原始分/规则偏差)
     */
    private ModelScoreSet scoreModel(String name, LlmClient client,
                                      EvaluationContext ctx, int repeatCount,
                                      Map<String, Double> ruleBaseline) {
        var strategy = strategyFactory.create(client);

        // 收集 repeatCount 次 scoreAll 结果
        var allRuns = new ArrayList<Map<String, LlmScoringStrategy.ScoreResult>>();
        for (int i = 0; i < repeatCount; i++) {
            try {
                allRuns.add(strategy.scoreAll(ctx));
            } catch (Exception e) {
                log.warn("[Compare] {} 第{}次打分失败: {}", name, i + 1, e.getMessage());
            }
        }

        // 按指标聚合
        var indicators = new LinkedHashMap<String, IndicatorStats>();
        // 收集所有指标编码
        var codes = new LinkedHashSet<String>();
        for (var run : allRuns) {
            codes.addAll(run.keySet());
        }

        for (String code : codes) {
            var scores = new ArrayList<BigDecimal>();
            for (var run : allRuns) {
                var sr = run.get(code);
                if (sr != null) scores.add(sr.score());
            }

            if (scores.isEmpty()) continue;

            // 均值
            double sum = scores.stream().mapToDouble(BigDecimal::doubleValue).sum();
            BigDecimal mean = BigDecimal.valueOf(sum / scores.size())
                    .setScale(2, java.math.RoundingMode.HALF_UP);

            // 标准差 (null if repeatCount=1 or only 1 valid run)
            Double stdDev = null;
            if (scores.size() > 1) {
                double avg = mean.doubleValue();
                double variance = scores.stream()
                        .mapToDouble(s -> Math.pow(s.doubleValue() - avg, 2))
                        .average().orElse(0);
                stdDev = Math.sqrt(variance);
            }

            // 规则偏差
            Double deviationFromRule = null;
            if (ruleBaseline.containsKey(code)) {
                deviationFromRule = mean.doubleValue() - ruleBaseline.get(code);
            }

            BigDecimal[] rawArr = scores.toArray(new BigDecimal[0]);
            indicators.put(code, new IndicatorStats(code, mean, stdDev, rawArr, deviationFromRule));
        }

        log.debug("[Compare] {} 打分完成: {} indicators × {} runs", name, indicators.size(), allRuns.size());
        return new ModelScoreSet(indicators, name);
    }

    /**
     * 提取规则引擎基线分数。
     * 规则引擎对所有指标打分，作为 LLM 模型打分的对比基线。
     */
    private Map<String, Double> extractRuleBaseline(EvaluationContext ctx) {
        try {
            if (ruleStrategy == null) return Map.of();
            var ruleScores = ruleStrategy.scoreAll(ctx);
            var baseline = new LinkedHashMap<String, Double>();
            ruleScores.forEach((k, v) -> baseline.put(k, v.score().doubleValue()));
            return baseline;
        } catch (Exception e) {
            log.debug("[Compare] 规则引擎基线提取失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 跨模型方差: 对各指标计算各模型均值间的方差。
     * 方差越大说明该指标在不同模型间的分歧越大。
     *
     * @param modelScores 各模型的打分结果集
     * @return 指标编码 → 方差映射
     */
    private Map<String, Double> computeCrossModelVariance(
            Map<String, ModelScoreSet> modelScores) {
        if (modelScores.size() <= 1) return Map.of();

        // 收集所有指标
        var allCodes = new LinkedHashSet<String>();
        for (var ms : modelScores.values()) {
            allCodes.addAll(ms.indicators().keySet());
        }

        var result = new LinkedHashMap<String, Double>();
        for (String code : allCodes) {
            var means = new ArrayList<Double>();
            for (var ms : modelScores.values()) {
                var stats = ms.indicators().get(code);
                if (stats != null) means.add(stats.meanScore().doubleValue());
            }
            if (means.size() <= 1) continue;

            double avg = means.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = means.stream()
                    .mapToDouble(m -> Math.pow(m - avg, 2))
                    .average().orElse(0);
            result.put(code, variance);
        }
        return result;
    }

    // === Records ===

    /**
     * 多模型对比结果。
     *
     * @param modelScores       各模型打分结果集 (模型名称 → ModelScoreSet)
     * @param ruleBaseline      规则引擎基线 (指标编码 → 分数)
     * @param crossModelVariance 跨模型方差 (指标编码 → 方差)
     * @param errors            模型错误 (模型名称 → 错误信息)
     * @param availableModelCount 成功打分的模型数
     * @param totalModelCount    总模型数
     */
    public record CompareResult(
        Map<String, ModelScoreSet> modelScores,
        Map<String, Double> ruleBaseline,
        Map<String, Double> crossModelVariance,
        Map<String, String> errors,
        int availableModelCount,
        int totalModelCount
    ) {}

    /**
     * 单个模型的打分结果集。
     *
     * @param indicators 指标编码 → 统计数据
     * @param model      模型名称
     */
    public record ModelScoreSet(
        Map<String, IndicatorStats> indicators,
        String model
    ) {}

    /**
     * 单指标的稳定性统计。
     *
     * @param indexCode        指标编码
     * @param meanScore        多次打分的均值
     * @param stdDev           标准差 (null 表示只有 1 次有效打分)
     * @param rawScores        每次打分的原始分数
     * @param deviationFromRule 与规则引擎基线的偏差 (null 表示无基线)
     */
    public record IndicatorStats(
        String indexCode,
        BigDecimal meanScore,
        Double stdDev,
        BigDecimal[] rawScores,
        Double deviationFromRule
    ) {}
}
