package io.github.accontra.eval;

import io.github.accontra.eval.application.event.EventRuleEvaluator;
import io.github.accontra.eval.domain.model.EvalModelEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S27: 事件规则引擎单元测试
 */
class EventRuleEvaluatorTest {

    private final EventRuleEvaluator evaluator = new EventRuleEvaluator();

    @Test
    void shouldTriggerRedLineWhenConditionMatches() {
        var event = newEvent("LOW_FILL", "RED_LINE",
                "rawValues[\"fill_rate\"] < 80", null, 1);
        var vars = Map.<String, Object>of(
                "rawValues", Map.of("fill_rate", 65.0));

        var triggered = evaluator.evaluate(List.of(event), vars, BigDecimal.valueOf(75));

        assertThat(triggered).hasSize(1);
        assertThat(triggered.get(0).code()).isEqualTo("LOW_FILL");
        assertThat(triggered.get(0).isRedLine()).isTrue();
    }

    @Test
    void shouldNotTriggerWhenConditionFails() {
        var event = newEvent("LOW_FILL", "RED_LINE",
                "rawValues[\"fill_rate\"] < 80", null, 1);
        var vars = Map.<String, Object>of(
                "rawValues", Map.of("fill_rate", 90.0));

        var triggered = evaluator.evaluate(List.of(event), vars, BigDecimal.valueOf(85));

        assertThat(triggered).isEmpty();
    }

    @Test
    void shouldEvaluateByPriority() {
        var e1 = newEvent("EVENT_A", "MARK",
                "rawValues[\"cost_deviation\"] > 10", null, 2);
        var e2 = newEvent("EVENT_B", "RED_LINE",
                "rawValues[\"cost_deviation\"] > 5", null, 1);
        var vars = Map.<String, Object>of(
                "rawValues", Map.of("cost_deviation", 12.0));

        // priority 1 (EVENT_B) fires before priority 2
        var triggered = evaluator.evaluate(List.of(e1, e2), vars, BigDecimal.valueOf(60));

        assertThat(triggered).hasSize(2);
        assertThat(triggered.get(0).code()).isEqualTo("EVENT_B"); // higher priority first
        assertThat(triggered.get(0).isRedLine()).isTrue();
    }

    @Test
    void shouldCalculateEventScore() {
        var event = newEvent("BONUS_5", "BONUS",
                "rawValues[\"fill_rate\"] > 80", "val * 1.1", 1);
        var vars = Map.<String, Object>of(
                "rawValues", Map.of("fill_rate", 90.0));

        var triggered = evaluator.evaluate(List.of(event), vars, BigDecimal.valueOf(80));

        assertThat(triggered).hasSize(1);
        assertThat(triggered.get(0).eventScore()).isEqualByComparingTo("88.0"); // 80 * 1.1
    }

    @Test
    void shouldSkipEventsWithoutDimensionRule() {
        var event = newEvent("NO_RULE", "MARK", null, null, 1);
        var vars = Map.<String, Object>of("rawValues", Map.of());

        var triggered = evaluator.evaluate(List.of(event), vars, BigDecimal.ZERO);

        assertThat(triggered).isEmpty();
    }

    private EvalModelEvent newEvent(String code, String type, String rule,
                                     String scoreExpr, int priority) {
        var e = new EvalModelEvent();
        e.setCode(code);
        e.setName(code + "_name");
        e.setEventType(type);
        e.setDimensionRule(rule);
        e.setScoreExpression(scoreExpr);
        e.setPriority(priority);
        e.setRedLineMessage("RED_LINE".equals(type) ? "红线触发" : null);
        e.setRiskLevel("RED_LINE".equals(type) ? "HIGH" : "MEDIUM");
        return e;
    }
}
