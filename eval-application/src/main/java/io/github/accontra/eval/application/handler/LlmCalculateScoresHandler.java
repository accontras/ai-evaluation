package io.github.accontra.eval.application.handler;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * H3 最简版 — LLM 打分 (不做 Stage 树、不路由、不派生)。
 * S9 调用 LlmScoringStrategy 得出每个指标的 LLM 分数，然后简单加权求和。
 */
public class LlmCalculateScoresHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(LlmCalculateScoresHandler.class);
    private final LlmScoringStrategy llmStrategy;

    public LlmCalculateScoresHandler(LlmScoringStrategy llmStrategy) {
        this.llmStrategy = llmStrategy;
    }

    @Override public String stepCode() { return "CALCULATE"; }
    @Override public String stepName() { return "LLM 计算得分"; }
    @Override public int order() { return 3; }

    @Override
    public void execute(EvaluationContext ctx) {
        var results = llmStrategy.scoreAll(ctx);

        // 简单平均: 权重信息在 stage 上 (S12 补树算分)，S9 先等权
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;

        for (var mi : ctx.getModelIndices()) {
            var ib = ctx.getIndexBaseMap() != null
                    ? ctx.getIndexBaseMap().get(String.valueOf(mi.getIndexId())) : null;
            if (ib == null) continue;

            var r = results.get(ib.getCode());
            if (r == null) continue;

            sum = sum.add(r.score());
            count++;

            log.info("[H3] {} = {}分 — {}", ib.getCode(), r.score(), r.reason());
        }

        if (count > 0) {
            ctx.setTotalScore(sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
        } else {
            ctx.setTotalScore(BigDecimal.ZERO);
        }

        log.info("[H3] totalScore = {}", ctx.getTotalScore());
    }
}
