package io.github.accontra.eval.application.strategy;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 双通道对比评分 —— 同一组指标, LLM 打一遍, 规则引擎打一遍, 逐指标对比差异。
 *
 * 差异分级:
 *   TRIVIAL (< 5%):  正常波动, 无需关注
 *   NOTABLE (5-15%): 值得关注, AI 可能看到规则看不到的东西
 *   SIGNIFICANT (> 15%): 需要人工仲裁
 */
@Component
public class DualChannelScoringService {

    private static final Logger log = LoggerFactory.getLogger(DualChannelScoringService.class);

    private final LlmScoringStrategy llmStrategy;
    private final RuleScoreStrategy ruleStrategy;

    public DualChannelScoringService(LlmScoringStrategy llmStrategy, RuleScoreStrategy ruleStrategy) {
        this.llmStrategy = llmStrategy;
        this.ruleStrategy = ruleStrategy;
    }

    /** 双通道打分 + 对比 */
    public CompareResult compare(EvaluationContext ctx) {
        log.info("[DualChannel] 开始双通道对比, bizId={}", ctx.getBizId());

        var llmScores = llmStrategy.scoreAll(ctx);
        var ruleScores = ruleStrategy.scoreAll(ctx);

        List<IndicatorDiff> diffs = new ArrayList<>();
        int significantCount = 0, notableCount = 0, trivialCount = 0;

        for (var entry : ruleScores.entrySet()) {
            String code = entry.getKey();
            var rule = entry.getValue();
            var llm = llmScores.get(code);

            if (llm == null) continue;

            BigDecimal diff = rule.score().subtract(llm.score()).abs();
            double ruleDouble = rule.score().doubleValue();
            double llmDouble = llm.score().doubleValue();
            double maxScore = 100.0;
            double pctDiff = maxScore > 0 ? diff.doubleValue() / maxScore : 0;

            String level;
            if (pctDiff < 0.05) { level = "TRIVIAL"; trivialCount++; }
            else if (pctDiff < 0.15) { level = "NOTABLE"; notableCount++; }
            else { level = "SIGNIFICANT"; significantCount++; }

            diffs.add(new IndicatorDiff(code, rule.indexName(),
                    BigDecimal.valueOf(ruleDouble), rule.reason(),
                    BigDecimal.valueOf(llmDouble), llm.reason(),
                    diff, level));
        }

        // 检查 LLM 独有但规则没有的指标
        for (var entry : llmScores.entrySet()) {
            if (!ruleScores.containsKey(entry.getKey())) {
                var llm = entry.getValue();
                diffs.add(new IndicatorDiff(entry.getKey(), llm.indexName(),
                        null, null,
                        llm.score(), llm.reason(),
                        llm.score(), "LLM_ONLY"));
            }
        }

        var result = new CompareResult(ctx.getBizId(), diffs, significantCount, notableCount, trivialCount);
        log.info("[DualChannel] 对比完成: total={}, SIG={}, NOTABLE={}, TRIVIAL={}",
                diffs.size(), significantCount, notableCount, trivialCount);
        return result;
    }

    public record IndicatorDiff(String indexCode, String indexName,
                                 BigDecimal ruleScore, String ruleReason,
                                 BigDecimal llmScore, String llmReason,
                                 BigDecimal diff, String diffLevel) {}

    public record CompareResult(String bizId, List<IndicatorDiff> diffs,
                                 int significantCount, int notableCount, int trivialCount) {}
}
