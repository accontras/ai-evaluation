package io.github.accontra.eval.application.strategy;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.common.util.ExpressionUtil;
import io.github.accontra.eval.domain.model.EvalIndex;
import io.github.accontra.eval.domain.model.EvalModelStandard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 规则引擎评分策略 — 参考标准匹配 + JEXL 条件 + 区间映射。
 *
 * 匹配链: 模型标准 → 通用标准 → 兜底 (默认 100)
 * standard_type: STRUCTURED / EXPRESSION → priority 排序
 * score_mode: INTERVAL_WEIGHT / RAW_WEIGHT / FIXED / FIXED_WEIGHT
 */
public class RuleScoreStrategy {

    private static final Logger log = LoggerFactory.getLogger(RuleScoreStrategy.class);

    /** 对 Context 中全部指标进行规则评分 */
    public Map<String, LlmScoringStrategy.ScoreResult> scoreAll(EvaluationContext ctx) {
        Map<String, LlmScoringStrategy.ScoreResult> results = new LinkedHashMap<>();
        var standards = ctx.getModelStandards();
        var rawValues = ctx.getRawValues();
        var indexBaseMap = ctx.getIndexBaseMap();

        if (ctx.getModelIndices() == null) return results;

        for (var mi : ctx.getModelIndices()) {
            EvalIndex ib = indexBaseMap != null
                    ? indexBaseMap.get(String.valueOf(mi.getIndexId())) : null;
            if (ib == null) continue;

            String code = ib.getCode();
            Object rawValue = rawValues != null ? rawValues.get(code) : null;
            BigDecimal score = scoreOne(standards, mi.getIndexId(), rawValue, ctx);
            results.put(code, new LlmScoringStrategy.ScoreResult(
                    code, ib.getName(), score,
                    String.format("规则引擎: raw=%.1f → score=%.1f (cap=%s floor=%s)",
                            toDouble(rawValue), score, mi.getScoreCap(), mi.getScoreFloor())));
        }
        return results;
    }

    /** 单指标评分 */
    BigDecimal scoreOne(List<EvalModelStandard> standards, Long indexId,
                        Object rawValue, EvaluationContext ctx) {

        if (standards == null || standards.isEmpty()) {
            return defaultScore(rawValue);
        }

        // 过滤该指标的标准, 按 priority 排序
        var matched = standards.stream()
                .filter(s -> Objects.equals(s.getIndexId(), indexId))
                .filter(s -> s.getStandardType() == null
                        || !s.getStandardType().startsWith("DEFAULT_"))
                .sorted(Comparator.comparing(s -> s.getPriority() != null ? s.getPriority() : 0))
                .toList();

        // 逐条匹配
        for (var std : matched) {
            if (evalCondition(std, rawValue, ctx)) {
                return calcScore(std, rawValue, ctx);
            }
        }

        // 未命中 → 兜底标准
        var fallback = standards.stream()
                .filter(s -> Objects.equals(s.getIndexId(), indexId))
                .filter(s -> s.getStandardType() != null && s.getStandardType().startsWith("DEFAULT_"))
                .findFirst().orElse(null);

        if (fallback != null) return calcScore(fallback, rawValue, ctx);
        return defaultScore(rawValue);
    }

    /** 条件匹配 */
    private boolean evalCondition(EvalModelStandard std, Object rawValue, EvaluationContext ctx) {
        // dimension_rule 非空 → JEXL
        if (std.getDimensionRule() != null && !std.getDimensionRule().isBlank()) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("val", rawValue);
            return ExpressionUtil.evalBool(std.getDimensionRule(), vars, null);
        }
        // min/max 区间
        if (std.getMinValue() != null || std.getMaxValue() != null) {
            if (rawValue == null) return false;
            BigDecimal rv = toBigDecimal(rawValue);
            if (std.getMinValue() != null && rv.compareTo(std.getMinValue()) < 0) return false;
            if (std.getMaxValue() != null && rv.compareTo(std.getMaxValue()) >= 0) return false;
            return true;
        }
        // 无条件 → 始终命中
        return true;
    }

    /** 得分计算 */
    private BigDecimal calcScore(EvalModelStandard std, Object rawValue, EvaluationContext ctx) {
        String mode = std.getScoreMode() != null ? std.getScoreMode() : "RAW_WEIGHT";
        BigDecimal rv = rawValue != null ? toBigDecimal(rawValue) : BigDecimal.ZERO;

        return switch (mode) {
            case "FIXED" -> std.getScore() != null ? std.getScore() : rv;
            case "FIXED_WEIGHT" -> {
                BigDecimal s = std.getScore() != null ? std.getScore() : BigDecimal.ZERO;
                yield s.multiply(BigDecimal.valueOf(100)); // weight as percentage
            }
            case "INTERVAL_WEIGHT" -> rv;  // S12 简化: 区间 weight 已在匹配时使用, 此处返回 raw
            default -> rv; // RAW_WEIGHT or default
        };
    }

    private BigDecimal defaultScore(Object rawValue) {
        return rawValue != null ? toBigDecimal(rawValue) : BigDecimal.valueOf(100);
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return 0;
    }
}
