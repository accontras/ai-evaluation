package io.github.accontra.eval.api.controller;

import io.github.accontra.eval.api.request.ExecuteEvaluationRequest;
import io.github.accontra.eval.api.response.ExecuteEvaluationResponse;
import io.github.accontra.eval.application.event.EventRuleEvaluator;
import io.github.accontra.eval.application.event.LlmEventDetector;
import io.github.accontra.eval.application.handler.*;
import io.github.accontra.eval.application.pipeline.ConfigurablePipeline;
import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.service.AiSummaryService;
import io.github.accontra.eval.application.service.ModelConfigCache;
import io.github.accontra.eval.application.service.MultiModelCompareService;
import io.github.accontra.eval.application.service.RankingService;
import io.github.accontra.eval.application.strategy.DualChannelScoringService;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.application.strategy.RuleScoreStrategy;
import io.github.accontra.eval.common.Result;
import io.github.accontra.eval.domain.model.EvalIndex;
import io.github.accontra.eval.domain.service.*;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import io.github.accontra.eval.infrastructure.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评估执行 Controller — S14: 双通道对比 + 统计。
 */
@RestController
@RequestMapping("/api/v1/evaluation")
public class EvaluationController {

    private static final Logger log = LoggerFactory.getLogger(EvaluationController.class);

    private final EvalIndexService indexService;
    private final LlmScoringStrategy llmStrategy;
    private final RuleScoreStrategy ruleStrategy;
    private final DualChannelScoringService dualChannel;
    private final LlmClient llmClient;
    private final EvalTaskLogMapper taskLogMapper;
    private final EvalObjectLogMapper objectLogMapper;
    private final EvalIndicatorLogMapper indicatorLogMapper;
    private final EvalModelEventMapper modelEventMapper;
    private final EvalEventLogMapper eventLogMapper;
    private final EvalGradeMappingMapper gradeMappingMapper;
    private final RankingService rankingService;
    private final ModelConfigCache configCache;
    private final MultiModelCompareService multiModelService;
    private final AiSummaryService summaryService;
    private final EvalAiExperimentMapper experimentMapper;

    public EvaluationController(EvalIndexService indexService,
                                LlmScoringStrategy llmStrategy, RuleScoreStrategy ruleStrategy,
                                DualChannelScoringService dualChannel,
                                LlmClient llmClient,
                                EvalTaskLogMapper taskLogMapper, EvalObjectLogMapper objectLogMapper,
                                EvalIndicatorLogMapper indicatorLogMapper,
                                EvalModelEventMapper modelEventMapper, EvalEventLogMapper eventLogMapper,
                                EvalGradeMappingMapper gradeMappingMapper,
                                ModelConfigCache configCache,
                                MultiModelCompareService multiModelService,
                                RankingService rankingService,
                                AiSummaryService summaryService,
                                EvalAiExperimentMapper experimentMapper) {
        this.indexService = indexService;
        this.llmStrategy = llmStrategy;
        this.ruleStrategy = ruleStrategy;
        this.dualChannel = dualChannel;
        this.llmClient = llmClient;
        this.taskLogMapper = taskLogMapper;
        this.objectLogMapper = objectLogMapper;
        this.indicatorLogMapper = indicatorLogMapper;
        this.modelEventMapper = modelEventMapper;
        this.eventLogMapper = eventLogMapper;
        this.gradeMappingMapper = gradeMappingMapper;
        this.configCache = configCache;
        this.multiModelService = multiModelService;
        this.rankingService = rankingService;
        this.summaryService = summaryService;
        this.experimentMapper = experimentMapper;
    }

    /** 执行单对象评估 — 双通道并行打分。 */
    @PostMapping("/execute")
    public Result<ExecuteEvaluationResponse> execute(@RequestBody ExecuteEvaluationRequest req) {
        log.info("收到评估请求: sceneCode={}, bizId={}", req.sceneCode(), req.bizId());

        var ctx = buildAndExecute(req);

        // 收集指标明细 — S14: 含 LLM + 规则双通道
        List<ExecuteEvaluationResponse.IndicatorResult> indicators = new ArrayList<>();
        var indexBaseMap = ctx.getIndexBaseMap();
        var llmScores = ctx.getLlmScores();
        var ruleScores = ctx.getRuleScores();

        if (ctx.getModelIndices() != null) {
            for (var mi : ctx.getModelIndices()) {
                EvalIndex ib = indexBaseMap != null
                        ? indexBaseMap.get(String.valueOf(mi.getIndexId())) : null;
                if (ib == null) continue;

                var llmR = llmScores != null ? llmScores.get(ib.getCode()) : null;
                var ruleR = ruleScores != null ? ruleScores.get(ib.getCode()) : null;

                indicators.add(new ExecuteEvaluationResponse.IndicatorResult(
                        ib.getCode(), ib.getName(),
                        llmR != null ? llmR.score() : null,
                        llmR != null ? llmR.reason() : (ruleR != null ? ruleR.reason() : "无数据")
                ));
            }
        }

        String scoringMode = "LLM";
        if (ctx.getIndicatorDiffs() != null) {
            long sigCount = ctx.getIndicatorDiffs().stream()
                    .filter(d -> "SIGNIFICANT".equals(d.diffLevel())).count();
            scoringMode = sigCount > 0 ? "DUAL_CHANNEL(SIG:" + sigCount + ")" : "DUAL_CHANNEL";
        }

        var resp = new ExecuteEvaluationResponse(
                ctx.getBizId(), ctx.getSceneCode(),
                ctx.getTotalScore(), ctx.getRiskLevel(),
                ctx.getGrade() != null ? ctx.getGrade() : "N/A",
                scoringMode, indicators);

        log.info("评估完成: bizId={}, totalScore={}, riskLevel={}, mode={}",
                resp.bizId(), resp.totalScore(), resp.riskLevel(), scoringMode);
        return Result.ok(resp);
    }

    /** 双通道对比统计 — S14。 */
    @GetMapping("/compare/stats")
    public Result<Map<String, Object>> compareStats() {
        var allLogs = indicatorLogMapper.selectList(null);
        if (allLogs == null || allLogs.isEmpty()) {
            return Result.ok(Map.of("message", "暂无对比数据"));
        }

        var withDiff = allLogs.stream()
                .filter(l -> l.getDiffLevel() != null)
                .toList();

        long total = withDiff.size();
        long sigCount = withDiff.stream().filter(l -> "SIGNIFICANT".equals(l.getDiffLevel())).count();
        long notableCount = withDiff.stream().filter(l -> "NOTABLE".equals(l.getDiffLevel())).count();
        long trivialCount = withDiff.stream().filter(l -> "TRIVIAL".equals(l.getDiffLevel())).count();

        double avgDiff = withDiff.stream()
                .filter(l -> l.getScoreDiff() != null)
                .mapToDouble(l -> l.getScoreDiff().doubleValue())
                .average().orElse(0);

        return Result.ok(Map.of(
                "totalCompared", total,
                "significantCount", sigCount,
                "notableCount", notableCount,
                "trivialCount", trivialCount,
                "significantRate", total > 0 ? String.format("%.1f%%", 100.0 * sigCount / total) : "0%",
                "avgDiff", String.format("%.2f", avgDiff)
        ));
    }

    /** Dashboard 数据 — 图表真实数据 */
    @GetMapping("/dashboard/{sceneCode}")
    public Result<Map<String, Object>> dashboard(@PathVariable("sceneCode") String sceneCode) {
        // 1. 等级分布
        var grades = new java.util.LinkedHashMap<String, Long>();
        var objQw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                io.github.accontra.eval.domain.model.EvalObjectLog>()
                .eq(io.github.accontra.eval.domain.model.EvalObjectLog::getSceneCode, sceneCode)
                .eq(io.github.accontra.eval.domain.model.EvalObjectLog::getStatus, "SUCCESS");
        var objects = objectLogMapper.selectList(objQw);
        long total=0, sumScore=0;
        for (var o : objects) {
            String g = o.getGrade() != null ? o.getGrade() : "N/A";
            grades.merge(g, 1L, Long::sum);
            total++;
            if (o.getTotalScore() != null) sumScore += o.getTotalScore().longValue();
        }

        // 2. LLM vs 规则对比 (关联 objectLog 获取 scene 过滤)
        var indicatorQw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                io.github.accontra.eval.domain.model.EvalIndicatorLog>()
                .isNotNull(io.github.accontra.eval.domain.model.EvalIndicatorLog::getDiffLevel)
                .orderByDesc(io.github.accontra.eval.domain.model.EvalIndicatorLog::getId)
                .last("LIMIT 200");
        var allIndicators = indicatorLogMapper.selectList(indicatorQw);
        // 收集 sceneCode 下的 objectLogId
        var sceneObjectIds = objects.stream()
                .map(io.github.accontra.eval.domain.model.EvalObjectLog::getId)
                .collect(java.util.stream.Collectors.toSet());
        var indicators = allIndicators.stream()
                .filter(i -> i.getObjectLogId() != null && sceneObjectIds.contains(i.getObjectLogId()))
                .toList();
        var diffList = indicators.stream().map(i -> Map.of(
                "indexCode", i.getIndexCode() != null ? i.getIndexCode() : "",
                "llmScore", i.getLlmScore() != null ? i.getLlmScore() : 0,
                "ruleScore", i.getRuleScore() != null ? i.getRuleScore() : 0,
                "diff", i.getScoreDiff() != null ? i.getScoreDiff() : 0,
                "diffLevel", i.getDiffLevel() != null ? i.getDiffLevel() : ""
        )).toList();

        // 3. 最近结果
        var recentQw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                io.github.accontra.eval.domain.model.EvalObjectLog>()
                .eq(io.github.accontra.eval.domain.model.EvalObjectLog::getSceneCode, sceneCode)
                .eq(io.github.accontra.eval.domain.model.EvalObjectLog::getStatus, "SUCCESS")
                .orderByDesc(io.github.accontra.eval.domain.model.EvalObjectLog::getId)
                .last("LIMIT 20");
        var recent = objectLogMapper.selectList(recentQw).stream().map(o -> Map.of(
                "targetCode", o.getTargetCode() != null ? o.getTargetCode() : "",
                "totalScore", o.getTotalScore() != null ? o.getTotalScore() : 0,
                "grade", o.getGrade() != null ? o.getGrade() : "N/A",
                "riskLevel", o.getRiskLevel() != null ? o.getRiskLevel() : "N/A",
                "evalRank", o.getEvalRank() != null ? o.getEvalRank() : 0,
                "rankTotal", o.getRankTotal() != null ? o.getRankTotal() : 0,
                "summaryStatus", o.getSummaryStatus() != null ? o.getSummaryStatus() : "N/A"
        )).toList();

        return Result.ok(Map.of(
                "sceneCode", sceneCode,
                "total", total,
                "avgScore", total > 0 ? String.format("%.1f", (double)sumScore/total) : "0",
                "gradeDistribution", grades,
                "indicatorDiffs", diffList,
                "recentResults", recent
        ));
    }

    /** 多模型对比 — S31 */
    @PostMapping("/compare-models")
    public Result<Map<String, Object>> compareModels(@RequestBody ExecuteEvaluationRequest req) {
        var ctx = buildAndExecute(req); // runs full pipeline
        var result = multiModelService.compare(ctx);
        return Result.ok(Map.of(
                "bizId", req.bizId(),
                "scores", result.modelScores(),
                "stdDevs", result.stdDevs()
        ));
    }

    /** AI 实验统计 — A2.2: P95 + 按模型/版本/指标分组 */
    @GetMapping("/experiments/stats")
    public Result<Map<String, Object>> experimentStats() {
        var all = experimentMapper.selectList(null);
        if (all == null || all.isEmpty()) return Result.ok(Map.of("message", "暂无实验数据"));

        long total = all.size();
        long errors = all.stream().filter(e -> e.getErrorType() != null).count();
        long totalTokens = all.stream().filter(e -> e.getInputTokens() != null)
                .mapToLong(e -> e.getInputTokens() + (e.getOutputTokens() != null ? e.getOutputTokens() : 0)).sum();

        // 延迟统计
        var durations = all.stream().filter(e -> e.getDurationMs() != null)
                .mapToLong(io.github.accontra.eval.domain.model.EvalAiExperiment::getDurationMs)
                .sorted().toArray();
        long avgDuration = durations.length > 0 ? Math.round(Arrays.stream(durations).average().orElse(0)) : 0;
        long p95Duration = durations.length > 0 ? durations[(int)(durations.length * 0.95)] : 0;

        // 按模型分组
        Map<String, Long> byModel = new LinkedHashMap<>();
        for (var e : all) {
            String m = e.getModel() != null ? e.getModel() : "unknown";
            byModel.merge(m, 1L, Long::sum);
        }

        // 按 prompt 版本分组
        Map<String, Map<String, Object>> byVersion = new LinkedHashMap<>();
        for (var e : all) {
            String v = e.getPromptVersion() != null ? e.getPromptVersion() : "unknown";
            byVersion.computeIfAbsent(v, k -> {
                var m = new LinkedHashMap<String, Object>();
                m.put("calls", 0L); m.put("totalTokens", 0L); m.put("errors", 0L); m.put("durations", new java.util.ArrayList<Long>());
                return m;
            });
            var m = byVersion.get(v);
            m.put("calls", (Long)m.get("calls") + 1);
            if (e.getInputTokens() != null) m.put("totalTokens", (Long)m.get("totalTokens") + e.getInputTokens() + (e.getOutputTokens() != null ? e.getOutputTokens() : 0));
            if (e.getErrorType() != null) m.put("errors", (Long)m.get("errors") + 1);
            if (e.getDurationMs() != null) ((java.util.ArrayList<Long>)m.get("durations")).add(e.getDurationMs());
        }
        // 计算每个版本的平均延迟
        Map<String, Object> versionStats = new LinkedHashMap<>();
        for (var entry : byVersion.entrySet()) {
            var m = entry.getValue();
            var durs = (java.util.ArrayList<Long>) m.get("durations");
            long avg = durs.isEmpty() ? 0 : Math.round(durs.stream().mapToLong(Long::longValue).average().orElse(0));
            versionStats.put(entry.getKey(), Map.of(
                    "calls", m.get("calls"),
                    "totalTokens", m.get("totalTokens"),
                    "avgDurationMs", avg,
                    "errorRate", (Long)m.get("calls") > 0
                            ? String.format("%.1f%%", 100.0 * (Long)m.get("errors") / (Long)m.get("calls")) : "0%"
            ));
        }

        return Result.ok(Map.of(
                "totalCalls", total,
                "avgDurationMs", avgDuration,
                "p95DurationMs", p95Duration,
                "totalTokens", totalTokens,
                "errorCount", errors,
                "errorRate", total > 0 ? String.format("%.1f%%", 100.0 * errors / total) : "0%",
                "byModel", byModel,
                "byVersion", versionStats
        ));
    }

    /** 异常检测 — A2.2: 3σ 异常标记 */
    @GetMapping("/experiments/anomalies")
    public Result<Map<String, Object>> experimentAnomalies() {
        var all = experimentMapper.selectList(null);
        if (all == null || all.isEmpty()) return Result.ok(Map.of("message", "暂无实验数据"));

        // 计算延迟均值 + 标准差
        var durs = all.stream().filter(e -> e.getDurationMs() != null)
                .mapToLong(io.github.accontra.eval.domain.model.EvalAiExperiment::getDurationMs).toArray();
        double durMean = Arrays.stream(durs).average().orElse(0);
        double durStd = Math.sqrt(Arrays.stream(durs).mapToDouble(d -> Math.pow(d - durMean, 2)).average().orElse(0));

        // 计算 token 均值 + 标准差
        var tokens = all.stream().filter(e -> e.getInputTokens() != null)
                .mapToInt(e -> e.getInputTokens() + (e.getOutputTokens() != null ? e.getOutputTokens() : 0)).toArray();
        double tokMean = Arrays.stream(tokens).average().orElse(0);
        double tokStd = Math.sqrt(Arrays.stream(tokens).mapToDouble(t -> Math.pow(t - tokMean, 2)).average().orElse(0));

        double durThreshold = durMean + 3 * durStd;
        double tokThreshold = tokMean + 3 * tokStd;

        // 找异常
        var slowOnes = all.stream().filter(e -> e.getDurationMs() != null && e.getDurationMs() > durThreshold)
                .sorted((a,b) -> Long.compare(b.getDurationMs(), a.getDurationMs()))
                .limit(5).map(e -> Map.of(
                        "id", e.getId(), "bizId", e.getBizId() != null ? e.getBizId() : "",
                        "durationMs", e.getDurationMs(), "type", "SLOW"))
                .toList();

        var heavyOnes = all.stream().filter(e -> e.getInputTokens() != null)
                .filter(e -> (e.getInputTokens() + (e.getOutputTokens() != null ? e.getOutputTokens() : 0)) > tokThreshold)
                .sorted((a,b) -> Integer.compare(
                        b.getInputTokens() + (b.getOutputTokens() != null ? b.getOutputTokens() : 0),
                        a.getInputTokens() + (a.getOutputTokens() != null ? a.getOutputTokens() : 0)))
                .limit(5).map(e -> Map.of(
                        "id", e.getId(), "bizId", e.getBizId() != null ? e.getBizId() : "",
                        "tokens", e.getInputTokens() + (e.getOutputTokens() != null ? e.getOutputTokens() : 0),
                        "type", "HEAVY_TOKEN"))
                .toList();

        var errors = all.stream().filter(e -> e.getErrorType() != null)
                .limit(5).map(e -> Map.of(
                        "id", e.getId(), "bizId", e.getBizId() != null ? e.getBizId() : "",
                        "errorType", e.getErrorType(), "type", "ERROR"))
                .toList();

        return Result.ok(Map.of(
                "durationMean", Math.round(durMean),
                "durationStd", Math.round(durStd),
                "durationThreshold", Math.round(durThreshold),
                "tokenMean", Math.round(tokMean),
                "tokenStd", Math.round(tokStd),
                "tokenThreshold", Math.round(tokThreshold),
                "slowQueries", slowOnes,
                "heavyTokenQueries", heavyOnes,
                "errorQueries", errors
        ));
    }

    /** 奥运排名 — S23 */
    @PostMapping("/rank/{sceneCode}")
    public Result<Map<String, Object>> rank(@PathVariable("sceneCode") String sceneCode) {
        int count = rankingService.rank(sceneCode);
        return Result.ok(Map.of("sceneCode", sceneCode, "ranked", count));
    }

    /** 生成 AI 总结 — S24 */
    @PostMapping("/summary/{objectLogId}")
    public Result<Map<String, Object>> generateSummary(@PathVariable("objectLogId") Long objectLogId) {
        String summary = summaryService.generateSummary(objectLogId);
        return Result.ok(Map.of("objectLogId", objectLogId, "summary", summary));
    }

    /** 查询 AI 总结 */
    @GetMapping("/summary/{objectLogId}")
    public Result<Map<String, Object>> getSummary(@PathVariable("objectLogId") Long objectLogId) {
        var obj = objectLogMapper.selectById(objectLogId);
        return Result.ok(Map.of(
                "objectLogId", objectLogId,
                "summary", obj != null ? obj.getSummary() : null,
                "summaryStatus", obj != null ? obj.getSummaryStatus() : null
        ));
    }

    private EvaluationContext buildAndExecute(ExecuteEvaluationRequest req) {
        var h1 = new ValidateAndLoadModelHandler(configCache, indexService);
        var h2 = new FetchIndicatorValuesHandler();
        var h3 = new LlmCalculateScoresHandler(llmStrategy, ruleStrategy, dualChannel);
        var h4 = new EventRedLineHandler(modelEventMapper, eventLogMapper,
                new EventRuleEvaluator(), new LlmEventDetector(llmClient));
        var h6 = new SummarizeResultHandler(taskLogMapper, objectLogMapper, indicatorLogMapper, gradeMappingMapper);

        var pipeline = new ConfigurablePipeline(List.of(h1, h2, h3, h4, h6));

        var ctx = new EvaluationContext();
        ctx.setSceneCode(req.sceneCode());
        ctx.setBizId(req.bizId());
        ctx.setDataPeriod(req.dataPeriod());
        ctx.setRawData(req.data());
        pipeline.execute(ctx);
        return ctx;
    }
}
