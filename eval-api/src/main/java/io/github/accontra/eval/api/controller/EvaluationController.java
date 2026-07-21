package io.github.accontra.eval.api.controller;

import io.github.accontra.eval.api.request.ExecuteEvaluationRequest;
import io.github.accontra.eval.api.response.ExecuteEvaluationResponse;
import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.service.EvaluationDomainService;
import io.github.accontra.eval.application.service.RagCompareTracker;
import io.github.accontra.eval.application.service.SimilarCaseService;
import io.github.accontra.eval.common.Result;
import io.github.accontra.eval.domain.model.EvalAiExperiment;
import io.github.accontra.eval.domain.model.EvalIndex;
import io.github.accontra.eval.infrastructure.mapper.EvalAiExperimentMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalObjectLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 评估执行 Controller — 只做参数校验 + Result 组装。业务逻辑在 EvaluationDomainService。
 */
@RestController
@RequestMapping("/api/v1/evaluation")
public class EvaluationController {

    private static final Logger log = LoggerFactory.getLogger(EvaluationController.class);

    private final EvaluationDomainService domainService;
    private final EvalObjectLogMapper objectLogMapper;
    private final EvalAiExperimentMapper experimentMapper;
    private final SimilarCaseService similarCaseService;
    private final RagCompareTracker ragCompareTracker;

    public EvaluationController(EvaluationDomainService domainService,
                                 EvalObjectLogMapper objectLogMapper,
                                 EvalAiExperimentMapper experimentMapper,
                                 SimilarCaseService similarCaseService,
                                 RagCompareTracker ragCompareTracker) {
        this.domainService = domainService;
        this.objectLogMapper = objectLogMapper;
        this.experimentMapper = experimentMapper;
        this.similarCaseService = similarCaseService;
        this.ragCompareTracker = ragCompareTracker;
    }

    /** 执行单对象评估 */
    @PostMapping("/execute")
    public Result<ExecuteEvaluationResponse> execute(@RequestBody ExecuteEvaluationRequest req) {
        log.info("评估请求: sceneCode={}, bizId={}", req.sceneCode(), req.bizId());
        var ctx = domainService.execute(req.sceneCode(), req.bizId(), req.dataPeriod(), req.data());
        return Result.ok(buildResponse(ctx));
    }

    /** 多模型对比 */
    @PostMapping("/compare-models")
    public Result<Map<String, Object>> compareModels(@RequestBody ExecuteEvaluationRequest req) {
        var ctx = domainService.execute(req.sceneCode(), req.bizId(), req.dataPeriod(), req.data());
        var result = domainService.compareModels(ctx);
        return Result.ok(Map.of("bizId", req.bizId(), "scores", result.modelScores(), "stdDevs", result.stdDevs()));
    }

    /** 双通道对比统计 */
    @GetMapping("/compare/stats")
    public Result<Map<String, Object>> compareStats() {
        return Result.ok(domainService.compareStats());
    }

    /** 奥运排名 */
    @PostMapping("/rank/{sceneCode}")
    public Result<Map<String, Object>> rank(@PathVariable("sceneCode") String sceneCode) {
        return Result.ok(Map.of("sceneCode", sceneCode, "ranked", domainService.rank(sceneCode)));
    }

    /** 生成 AI 总结 */
    @PostMapping("/summary/{objectLogId}")
    public Result<Map<String, Object>> generateSummary(@PathVariable("objectLogId") Long objectLogId) {
        return Result.ok(Map.of("objectLogId", objectLogId, "summary", domainService.generateSummary(objectLogId)));
    }

    /** 查询 AI 总结 */
    @GetMapping("/summary/{objectLogId}")
    public Result<Map<String, Object>> getSummary(@PathVariable("objectLogId") Long objectLogId) {
        var obj = objectLogMapper.selectById(objectLogId);
        return Result.ok(Map.of("objectLogId", objectLogId,
                "summary", obj != null ? obj.getSummary() : null,
                "summaryStatus", obj != null ? obj.getSummaryStatus() : null));
    }

    /** Dashboard 数据 */
    @GetMapping("/dashboard/{sceneCode}")
    public Result<Map<String, Object>> dashboard(@PathVariable("sceneCode") String sceneCode) {
        var qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                io.github.accontra.eval.domain.model.EvalObjectLog>()
                .eq(io.github.accontra.eval.domain.model.EvalObjectLog::getSceneCode, sceneCode)
                .eq(io.github.accontra.eval.domain.model.EvalObjectLog::getStatus, "SUCCESS");
        var objects = objectLogMapper.selectList(qw);
        var grades = new LinkedHashMap<String, Long>();
        long total = 0, sumScore = 0;
        for (var o : objects) {
            grades.merge(o.getGrade() != null ? o.getGrade() : "N/A", 1L, Long::sum);
            total++;
            if (o.getTotalScore() != null) sumScore += o.getTotalScore().longValue();
        }
        var recent = objects.stream().sorted((a, b) -> Long.compare(b.getId(), a.getId())).limit(20)
                .map(o -> Map.of("targetCode", o.getTargetCode() != null ? o.getTargetCode() : "",
                        "totalScore", o.getTotalScore() != null ? o.getTotalScore() : 0,
                        "grade", o.getGrade() != null ? o.getGrade() : "N/A",
                        "riskLevel", o.getRiskLevel() != null ? o.getRiskLevel() : "N/A",
                        "evalRank", o.getEvalRank() != null ? o.getEvalRank() : 0,
                        "rankTotal", o.getRankTotal() != null ? o.getRankTotal() : 0))
                .toList();
        return Result.ok(Map.of("sceneCode", sceneCode, "total", total,
                "avgScore", total > 0 ? String.format("%.1f", (double) sumScore / total) : "0",
                "gradeDistribution", grades, "indicatorDiffs", List.of(), "recentResults", recent));
    }

    /** AI 实验统计 */
    @GetMapping("/experiments/stats")
    public Result<Map<String, Object>> experimentStats() {
        var all = experimentMapper.selectList(null);
        if (all == null || all.isEmpty()) return Result.ok(Map.of("message", "暂无实验数据"));
        long total = all.size();
        var durations = all.stream().filter(e -> e.getDurationMs() != null)
                .mapToLong(EvalAiExperiment::getDurationMs).sorted().toArray();
        long avg = durations.length > 0 ? Math.round(Arrays.stream(durations).average().orElse(0)) : 0;
        long p95 = durations.length > 0 ? durations[(int) (durations.length * 0.95)] : 0;
        long tokens = all.stream().filter(e -> e.getInputTokens() != null)
                .mapToLong(e -> e.getInputTokens() + (e.getOutputTokens() != null ? e.getOutputTokens() : 0)).sum();
        long errors = all.stream().filter(e -> e.getErrorType() != null).count();
        double cost = all.stream().filter(e -> e.getCost() != null).mapToDouble(e -> e.getCost().doubleValue()).sum();
        return Result.ok(Map.of("totalCalls", total, "avgDurationMs", avg, "p95DurationMs", p95,
                "totalTokens", tokens, "totalCost", String.format("%.6f", cost),
                "errorCount", errors, "errorRate", total > 0 ? String.format("%.1f%%", 100.0 * errors / total) : "0%"));
    }

    /** A3 RAG: 相似案例检索 */
    @GetMapping("/similar-cases/{indexCode}/{value}")
    public Result<Map<String, Object>> similarCases(@PathVariable("indexCode") String indexCode,
                                                      @PathVariable("value") double value) {
        var cases = similarCaseService.findSimilar(indexCode, value, 5);
        var list = cases.stream().map(c -> Map.of(
                "indexCode", c.log().getIndexCode(),
                "llmScore", c.log().getLlmScore(),
                "ruleScore", c.log().getRuleScore(),
                "diffLevel", c.log().getDiffLevel(),
                "reason", c.log().getLlmReason() != null ? c.log().getLlmReason().substring(0, Math.min(80, c.log().getLlmReason().length())) : "",
                "similarity", Math.round(c.similarity())
        )).toList();
        return Result.ok(Map.of("indexCode", indexCode, "value", value, "cases", list));
    }

    /** A3.3 RAG 影子模式: 向量 vs 规则对比统计 */
    @GetMapping("/rag-compare/stats")
    public Result<Map<String, Object>> ragCompareStats() {
        return Result.ok(ragCompareTracker.getStats());
    }

    /** A4: 韧性状态 */
    @GetMapping("/resilience")
    public Result<Map<String, Object>> resilienceStatus() {
        return Result.ok(domainService.getResilienceStatus());
    }

    /** 异常检测 */
    @GetMapping("/experiments/anomalies")
    public Result<Map<String, Object>> experimentAnomalies() {
        var all = experimentMapper.selectList(null);
        if (all == null || all.isEmpty()) return Result.ok(Map.of("message", "暂无实验数据"));
        var durs = all.stream().filter(e -> e.getDurationMs() != null)
                .mapToLong(EvalAiExperiment::getDurationMs).toArray();
        double dm = Arrays.stream(durs).average().orElse(0);
        double ds = Math.sqrt(Arrays.stream(durs).mapToDouble(d -> Math.pow(d - dm, 2)).average().orElse(0));
        double dt = dm + 3 * ds;
        var toks = all.stream().filter(e -> e.getInputTokens() != null)
                .mapToInt(e -> e.getInputTokens() + (e.getOutputTokens() != null ? e.getOutputTokens() : 0)).toArray();
        double tm = Arrays.stream(toks).average().orElse(0);
        double ts = Math.sqrt(Arrays.stream(toks).mapToDouble(t -> Math.pow(t - tm, 2)).average().orElse(0));
        double tt = tm + 3 * ts;
        return Result.ok(Map.of("durationMean", Math.round(dm), "durationThreshold", Math.round(dt),
                "tokenMean", Math.round(tm), "tokenThreshold", Math.round(tt),
                "slowQueries", all.stream().filter(e -> e.getDurationMs() != null && e.getDurationMs() > dt).limit(5)
                        .map(e -> Map.of("id", e.getId(), "durationMs", e.getDurationMs(), "type", "SLOW")).toList(),
                "heavyTokenQueries", List.of(), "errorQueries",
                all.stream().filter(e -> e.getErrorType() != null).limit(5)
                        .map(e -> Map.of("id", e.getId(), "errorType", e.getErrorType(), "type", "ERROR")).toList()));
    }

    private ExecuteEvaluationResponse buildResponse(EvaluationContext ctx) {
        var indicators = new ArrayList<ExecuteEvaluationResponse.IndicatorResult>();
        var ibm = ctx.getIndexBaseMap();
        var ls = ctx.getLlmScores();
        if (ctx.getModelIndices() != null) {
            for (var mi : ctx.getModelIndices()) {
                EvalIndex ib = ibm != null ? ibm.get(String.valueOf(mi.getIndexId())) : null;
                if (ib == null) continue;
                var sr = ls != null ? ls.get(ib.getCode()) : null;
                indicators.add(new ExecuteEvaluationResponse.IndicatorResult(
                        ib.getCode(), ib.getName(), sr != null ? sr.score() : null,
                        sr != null ? sr.reason() : "无数据"));
            }
        }
        String mode = "LLM";
        if (ctx.getIndicatorDiffs() != null) {
            long sig = ctx.getIndicatorDiffs().stream().filter(d -> "SIGNIFICANT".equals(d.diffLevel())).count();
            mode = sig > 0 ? "DUAL_CHANNEL(SIG:" + sig + ")" : "DUAL_CHANNEL";
        }
        return new ExecuteEvaluationResponse(ctx.getBizId(), ctx.getSceneCode(), ctx.getTotalScore(),
                ctx.getRiskLevel(), ctx.getGrade() != null ? ctx.getGrade() : "N/A", mode, indicators);
    }
}
