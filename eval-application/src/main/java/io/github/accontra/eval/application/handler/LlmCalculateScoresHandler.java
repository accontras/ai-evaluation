package io.github.accontra.eval.application.handler;

import io.github.accontra.eval.application.pipeline.*;
import io.github.accontra.eval.application.strategy.DualChannelScoringService;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.application.strategy.RuleScoreStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * H3 — 双通道打分 + Stage 树聚合。
 *
 * S14: LLM + 规则并行打分，逐指标对比。
 * S15: Stage 树装配 + 自底向上聚合，取代简单平均。
 *
 * 分工铁律: LLM 在 leaf 层打完分就停下，树聚合永远由规则引擎负责。
 */
public class LlmCalculateScoresHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(LlmCalculateScoresHandler.class);
    private final LlmScoringStrategy llmStrategy;
    private final RuleScoreStrategy ruleStrategy;
    private final DualChannelScoringService dualChannel;

    public LlmCalculateScoresHandler(LlmScoringStrategy llmStrategy,
                                     RuleScoreStrategy ruleStrategy,
                                     DualChannelScoringService dualChannel) {
        this.llmStrategy = llmStrategy;
        this.ruleStrategy = ruleStrategy;
        this.dualChannel = dualChannel;
    }

    @Override public String stepCode() { return "CALCULATE"; }
    @Override public String stepName() { return "双通道计算得分 + 树聚合"; }
    @Override public int order() { return 3; }

    @Override
    public void execute(EvaluationContext ctx) {
        // 1. Stage 树装配
        var assembler = new StageNodeAssembler();
        var root = assembler.assembleSingle(ctx.getStages(), ctx.getModelIndices());
        ctx.setRootStageNode(root);

        // 2. 双通道 parallel 打分
        var compareResult = dualChannel.compare(ctx);
        ctx.setIndicatorDiffs(compareResult.diffs());

        var llmScores = llmStrategy.scoreAll(ctx);
        ctx.setLlmScores(llmScores);

        var ruleScores = ruleStrategy.scoreAll(ctx);
        ctx.setRuleScores(ruleScores);

        // 3. 树聚合: LLM 分数为主 (leaf 层), 规则引擎负责聚合 (往上全是数学)
        var aggregator = new TreeAggregator();
        var defaultAggMode = ctx.getModel() != null && ctx.getModel().getAggregateMode() != null
                ? ctx.getModel().getAggregateMode() : "weighted_sum";

        BigDecimal totalScore;
        if (root != null) {
            // S16: 传入 attrValues 供 TOP 路由 JEXL 条件求值 (attrValues["dept"] == "LOGISTICS")
            Map<String, Object> routingVars = Map.of("attrValues",
                    ctx.getAttrValues() != null ? ctx.getAttrValues() : Map.of());
            totalScore = aggregator.aggregate(root, llmScores, ctx.getIndexBaseMap(),
                    defaultAggMode, routingVars);
        } else {
            // 降级: 无 Stage 树 → 简单平均
            log.warn("Stage tree is null, falling back to simple average");
            totalScore = simpleAverage(llmScores, ruleScores, ctx);
        }

        ctx.setTotalScore(totalScore);

        // 4. 日志
        log.info("[H3] totalScore={} (tree aggregated), SIG={}, NOTABLE={}, TRIVIAL={}",
                totalScore,
                compareResult.significantCount(),
                compareResult.notableCount(),
                compareResult.trivialCount());

        if (root != null) {
            logStageTree(root, 0);
        }
    }

    /** 降级: 简单平均 (无树结构时) */
    private BigDecimal simpleAverage(
            Map<String, LlmScoringStrategy.ScoreResult> llmScores,
            Map<String, LlmScoringStrategy.ScoreResult> ruleScores,
            EvaluationContext ctx) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (var mi : ctx.getModelIndices()) {
            var ib = ctx.getIndexBaseMap() != null
                    ? ctx.getIndexBaseMap().get(String.valueOf(mi.getIndexId())) : null;
            if (ib == null) continue;
            var llmR = llmScores != null ? llmScores.get(ib.getCode()) : null;
            var ruleR = ruleScores != null ? ruleScores.get(ib.getCode()) : null;
            BigDecimal score = llmR != null ? llmR.score()
                    : ruleR != null ? ruleR.score() : null;
            if (score == null) continue;
            sum = sum.add(score);
            count++;
        }
        return count > 0
                ? sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private void logStageTree(StageNode node, int depth) {
        var prefix = "  ".repeat(depth);
        log.info("{}[{}] {} type={} score={} indices={} children={}",
                prefix, node.getStage().getCode(), node.getStage().getName(),
                node.getStage().getType(), node.getScore(),
                node.getIndices().size(), node.getChildren().size());
        for (var child : node.getChildren()) {
            logStageTree(child, depth + 1);
        }
    }
}
