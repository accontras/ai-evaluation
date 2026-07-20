package io.github.accontra.eval.application.handler;

import io.github.accontra.eval.application.event.EventRuleEvaluator;
import io.github.accontra.eval.application.event.LlmEventDetector;
import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.domain.model.EvalEventLog;
import io.github.accontra.eval.infrastructure.mapper.EvalEventLogMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalModelEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * H4 — 事件/红线检测（双通道）。
 *
 * 规则引擎通道: JEXL 条件匹配 → RED_LINE / BONUS / DEDUCT
 * LLM 通道: Prompt 异常检测 → hasAnomaly / riskLevel / candidates
 * 对比: RULE_ONLY / LLM_ONLY / BOTH
 *
 * 红线判定: 任一通道触发 RED_LINE → ctx.blocked=true
 */
public class EventRedLineHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(EventRedLineHandler.class);

    private final EvalModelEventMapper modelEventMapper;
    private final EvalEventLogMapper eventLogMapper;
    private final EventRuleEvaluator ruleEvaluator;
    private final LlmEventDetector llmDetector;

    public EventRedLineHandler(EvalModelEventMapper modelEventMapper,
                                EvalEventLogMapper eventLogMapper,
                                EventRuleEvaluator ruleEvaluator,
                                LlmEventDetector llmDetector) {
        this.modelEventMapper = modelEventMapper;
        this.eventLogMapper = eventLogMapper;
        this.ruleEvaluator = ruleEvaluator;
        this.llmDetector = llmDetector;
    }

    @Override public String stepCode() { return "EVENT"; }
    @Override public String stepName() { return "事件/红线检测"; }
    @Override public int order() { return 4; }

    @Override
    public void execute(EvaluationContext ctx) {
        // 1. 加载事件配置
        var modelId = ctx.getModel() != null ? ctx.getModel().getId() : null;
        if (modelId == null) {
            log.warn("[H4] model is null, skipping event detection");
            return;
        }
        var events = modelEventMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        io.github.accontra.eval.domain.model.EvalModelEvent>()
                        .eq(io.github.accontra.eval.domain.model.EvalModelEvent::getModelId, modelId)
                        .eq(io.github.accontra.eval.domain.model.EvalModelEvent::getEnabled, 1));
        if (events == null) events = List.of();

        // 2. 规则引擎通道
        Map<String, Object> routingVars = buildRoutingVars(ctx);
        var ruleTriggered = ruleEvaluator.evaluate(events, routingVars, ctx.getTotalScore());

        // 3. LLM 通道 (只在有事件配置时才调用 LLM，节省 token)
        LlmEventDetector.AnomalyResult llmResult = null;
        if (!events.isEmpty()) {
            llmResult = llmDetector.detect(ctx);
        }

        // 4. 双通道对比 + 落库
        boolean ruleRedLine = ruleTriggered.stream().anyMatch(EventRuleEvaluator.TriggeredEvent::isRedLine);
        boolean llmRedLine = llmResult != null && llmResult.hasAnomaly()
                && "HIGH".equals(llmResult.riskLevel());

        // 判定阻塞
        ctx.setBlocked(ruleRedLine || llmRedLine);

        // 写入事件日志
        int sn = 0;
        // 规则通道事件
        for (var te : ruleTriggered) {
            EvalEventLog logEntry = buildEventLog(te, ctx, ++sn);
            // 对比 LLM
            boolean llmAlsoDetected = llmResult != null && llmResult.hasAnomaly()
                    && llmResult.redLineCandidates().stream()
                    .anyMatch(c -> c.contains(te.code()) || te.name().contains(c));
            logEntry.setTriggerSource(llmAlsoDetected ? "BOTH" : "RULE");
            eventLogMapper.insert(logEntry);
        }
        // LLM 独有异常 (规则引擎未触发)
        if (llmResult != null && llmResult.hasAnomaly() && llmResult.redLineCandidates() != null) {
            for (String candidate : llmResult.redLineCandidates()) {
                boolean alreadyCovered = ruleTriggered.stream()
                        .anyMatch(te -> te.code().contains(candidate) || candidate.contains(te.code()));
                if (!alreadyCovered) {
                    EvalEventLog logEntry = new EvalEventLog();
                    logEntry.setObjectLogId(null); // H6 回填
                    logEntry.setBizId(ctx.getBizId());
                    logEntry.setSceneCode(ctx.getSceneCode());
                    logEntry.setEventCode("LLM_" + candidate.replaceAll("[^a-zA-Z0-9]", "_"));
                    logEntry.setEventName(candidate);
                    logEntry.setEventType("RED_LINE");
                    logEntry.setRedLineMessage(llmResult.description());
                    logEntry.setIsRedLine("Y");
                    logEntry.setTriggerSource("LLM");
                    logEntry.setSn(++sn);
                    logEntry.setEnabled(1);
                    logEntry.setStatus("ACTIVE");
                    eventLogMapper.insert(logEntry);
                }
            }
        }

        // 红线调整总分
        if (ctx.isBlocked() && ctx.getTotalScore() != null) {
            ctx.setAdjustedTotalScore(ctx.getTotalScore().multiply(BigDecimal.valueOf(0.6))
                    .setScale(2, RoundingMode.HALF_UP));
            log.warn("[H4] 红线触发! 原始分={} → 调整分={}",
                    ctx.getTotalScore(), ctx.getAdjustedTotalScore());
        } else {
            ctx.setAdjustedTotalScore(ctx.getTotalScore());
        }

        log.info("[H4] 事件检测完成: ruleTriggered={}, llmAnomaly={}, blocked={}",
                ruleTriggered.size(), llmResult != null ? llmResult.hasAnomaly() : false, ctx.isBlocked());
    }

    private Map<String, Object> buildRoutingVars(EvaluationContext ctx) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("attrValues", ctx.getAttrValues() != null ? ctx.getAttrValues() : Map.of());
        vars.put("rawValues", ctx.getRawValues() != null ? ctx.getRawValues() : Map.of());
        vars.put("totalScore", ctx.getTotalScore() != null ? ctx.getTotalScore() : BigDecimal.ZERO);
        return vars;
    }

    private EvalEventLog buildEventLog(EventRuleEvaluator.TriggeredEvent te,
                                        EvaluationContext ctx, int sn) {
        EvalEventLog logEntry = new EvalEventLog();
        logEntry.setBizId(ctx.getBizId());
        logEntry.setSceneCode(ctx.getSceneCode());
        logEntry.setEventCode(te.code());
        logEntry.setEventName(te.name());
        logEntry.setEventType(te.eventType());
        logEntry.setDimensionRule(te.modelEvent().getDimensionRule());
        logEntry.setEventScore(te.eventScore());
        logEntry.setRedLineMessage(te.redLineMessage());
        logEntry.setIsRedLine(te.isRedLine() ? "Y" : "N");
        logEntry.setSn(sn);
        logEntry.setEnabled(1);
        logEntry.setStatus("ACTIVE");
        return logEntry;
    }
}
