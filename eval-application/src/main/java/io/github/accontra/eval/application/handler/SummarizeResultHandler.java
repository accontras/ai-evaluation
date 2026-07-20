package io.github.accontra.eval.application.handler;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.domain.model.EvalIndicatorLog;
import io.github.accontra.eval.domain.model.EvalObjectLog;
import io.github.accontra.eval.domain.model.EvalTaskLog;
import io.github.accontra.eval.infrastructure.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * H6 — 汇总结果并落库。
 * S9 最简版: 无等级映射、无 AI 总结、无证据链。
 */
public class SummarizeResultHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(SummarizeResultHandler.class);

    private final EvalTaskLogMapper taskLogMapper;
    private final EvalObjectLogMapper objectLogMapper;
    private final EvalIndicatorLogMapper indicatorLogMapper;

    public SummarizeResultHandler(EvalTaskLogMapper taskLogMapper,
                                  EvalObjectLogMapper objectLogMapper,
                                  EvalIndicatorLogMapper indicatorLogMapper) {
        this.taskLogMapper = taskLogMapper;
        this.objectLogMapper = objectLogMapper;
        this.indicatorLogMapper = indicatorLogMapper;
    }

    @Override public String stepCode() { return "SUMMARIZE"; }
    @Override public String stepName() { return "汇总落库"; }
    @Override public int order() { return 6; }

    @Override
    public void execute(EvaluationContext ctx) {
        BigDecimal totalScore = ctx.getTotalScore() != null ? ctx.getTotalScore() : BigDecimal.ZERO;

        // 风险等级
        String riskLevel;
        if (ctx.isBlocked() || totalScore.compareTo(BigDecimal.valueOf(60)) < 0) {
            riskLevel = "HIGH";
        } else if (totalScore.compareTo(BigDecimal.valueOf(80)) < 0) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }
        ctx.setRiskLevel(riskLevel);

        // 1. 创建任务日志
        EvalTaskLog taskLog = new EvalTaskLog();
        taskLog.setCode("TASK-" + System.currentTimeMillis());
        taskLog.setSceneCode(ctx.getSceneCode());
        taskLog.setStatus("SUCCESS");
        taskLog.setStartTime(LocalDateTime.now());
        taskLog.setEndTime(LocalDateTime.now());
        taskLog.setEnabled(1);
        taskLogMapper.insert(taskLog);

        // 2. 创建对象日志
        EvalObjectLog objectLog = new EvalObjectLog();
        objectLog.setTaskLogId(taskLog.getId());
        objectLog.setSceneCode(ctx.getSceneCode());
        objectLog.setTargetCode(ctx.getBizId());
        objectLog.setTotalScore(totalScore);
        objectLog.setRiskLevel(riskLevel);
        objectLog.setStatus("SUCCESS");
        objectLog.setEnabled(1);
        objectLogMapper.insert(objectLog);

        // 3. 创建指标日志 (暂不写具体分数明细，S12 补)
        if (ctx.getRawValues() != null) {
            int sn = 0;
            for (var entry : ctx.getRawValues().entrySet()) {
                EvalIndicatorLog il = new EvalIndicatorLog();
                il.setObjectLogId(objectLog.getId());
                il.setTaskLogId(taskLog.getId());
                il.setIndexCode(entry.getKey());
                il.setClazz("INDEX");
                il.setDataValue(entry.getValue() != null ? entry.getValue().toString() : null);
                il.setSn(++sn);
                il.setEnabled(1);
                indicatorLogMapper.insert(il);
            }
        }

        log.info("[H6] 落库完成: taskLog={}, objectLog={}, totalScore={}, riskLevel={}",
                taskLog.getId(), objectLog.getId(), totalScore, riskLevel);
    }
}
