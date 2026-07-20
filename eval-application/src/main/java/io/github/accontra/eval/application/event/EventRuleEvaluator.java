package io.github.accontra.eval.application.event;

import io.github.accontra.eval.common.util.ExpressionUtil;
import io.github.accontra.eval.domain.model.EvalModelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

/**
 * 事件规则引擎 — 按 priority 排序评估 JEXL 事件条件。
 *
 * 事件类型: RED_LINE / BONUS / DEDUCT / MARK
 * 条件: dimensionRule (JEXL 表达式)
 * 得分: scoreExpression (JEXL 表达式，可选)
 */
public class EventRuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(EventRuleEvaluator.class);

    /**
     * 评估所有事件配置，返回触发的事件列表。
     *
     * @param events      事件配置列表
     * @param routingVars JEXL 变量上下文 (attrValues + rawValues)
     * @param totalScore  当前总分 (用于 scoreExpression 求值)
     */
    public List<TriggeredEvent> evaluate(List<EvalModelEvent> events,
                                          Map<String, Object> routingVars,
                                          BigDecimal totalScore) {
        if (events == null || events.isEmpty()) return List.of();

        var sorted = events.stream()
                .filter(e -> e.getDimensionRule() != null && !e.getDimensionRule().isBlank())
                .sorted(Comparator.comparing(e -> e.getPriority() != null ? e.getPriority() : Integer.MAX_VALUE))
                .toList();

        List<TriggeredEvent> triggered = new ArrayList<>();
        for (var event : sorted) {
            try {
                boolean matched = ExpressionUtil.evalBool(event.getDimensionRule(), routingVars, null);
                if (matched) {
                    BigDecimal eventScore = calcEventScore(event, totalScore);
                    triggered.add(new TriggeredEvent(event, eventScore, "RULE"));
                    log.info("[EventRule] 触发: {} ({}), priority={}, score={}",
                            event.getCode(), event.getEventType(), event.getPriority(), eventScore);
                }
            } catch (Exception e) {
                log.warn("[EventRule] JEXL 求值失败: {} rule={} err={}",
                        event.getCode(), event.getDimensionRule(), e.getMessage());
            }
        }
        return triggered;
    }

    private BigDecimal calcEventScore(EvalModelEvent event, BigDecimal totalScore) {
        if (event.getScoreExpression() == null || event.getScoreExpression().isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("val", totalScore);
            return ExpressionUtil.eval(event.getScoreExpression(), vars, null);
        } catch (Exception e) {
            log.warn("[EventRule] scoreExpression 求值失败: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** 触发事件结果 */
    public record TriggeredEvent(EvalModelEvent modelEvent, BigDecimal eventScore, String source) {
        public String code() { return modelEvent.getCode(); }
        public String name() { return modelEvent.getName(); }
        public String eventType() { return modelEvent.getEventType(); }
        public String redLineMessage() { return modelEvent.getRedLineMessage(); }
        public String riskLevel() { return modelEvent.getRiskLevel(); }
        public boolean isRedLine() { return "RED_LINE".equals(modelEvent.getEventType()); }
    }
}
