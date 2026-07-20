package io.github.accontra.eval.api.controller;

import io.github.accontra.eval.api.request.ExecuteEvaluationRequest;
import io.github.accontra.eval.api.response.ExecuteEvaluationResponse;
import io.github.accontra.eval.application.event.EventRuleEvaluator;
import io.github.accontra.eval.application.event.LlmEventDetector;
import io.github.accontra.eval.application.handler.*;
import io.github.accontra.eval.application.pipeline.ConfigurablePipeline;
import io.github.accontra.eval.application.pipeline.EvaluationContext;
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
import java.util.List;
import java.util.Map;

/**
 * 评估执行 Controller — S14: 双通道对比 + 统计。
 */
@RestController
@RequestMapping("/api/v1/evaluation")
public class EvaluationController {

    private static final Logger log = LoggerFactory.getLogger(EvaluationController.class);

    private final EvalSceneService sceneService;
    private final EvalModelService modelService;
    private final EvalModelStageService stageService;
    private final EvalModelIndexService modelIndexService;
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

    public EvaluationController(EvalSceneService sceneService, EvalModelService modelService,
                                EvalModelStageService stageService, EvalModelIndexService modelIndexService,
                                EvalIndexService indexService,
                                LlmScoringStrategy llmStrategy, RuleScoreStrategy ruleStrategy,
                                DualChannelScoringService dualChannel,
                                LlmClient llmClient,
                                EvalTaskLogMapper taskLogMapper, EvalObjectLogMapper objectLogMapper,
                                EvalIndicatorLogMapper indicatorLogMapper,
                                EvalModelEventMapper modelEventMapper, EvalEventLogMapper eventLogMapper,
                                EvalGradeMappingMapper gradeMappingMapper, RankingService rankingService) {
        this.sceneService = sceneService;
        this.modelService = modelService;
        this.stageService = stageService;
        this.modelIndexService = modelIndexService;
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
        this.rankingService = rankingService;
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

    /** 奥运排名 — S23 */
    @PostMapping("/rank/{sceneCode}")
    public Result<Map<String, Object>> rank(@PathVariable("sceneCode") String sceneCode) {
        int count = rankingService.rank(sceneCode);
        return Result.ok(Map.of("sceneCode", sceneCode, "ranked", count));
    }

    private EvaluationContext buildAndExecute(ExecuteEvaluationRequest req) {
        var h1 = new ValidateAndLoadModelHandler(sceneService, modelService,
                stageService, modelIndexService, indexService);
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
