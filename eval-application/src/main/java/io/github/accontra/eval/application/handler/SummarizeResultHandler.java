package io.github.accontra.eval.application.handler;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.strategy.DualChannelScoringService;
import io.github.accontra.eval.domain.model.EvalGradeMapping;
import io.github.accontra.eval.domain.model.EvalIndicatorLog;
import io.github.accontra.eval.domain.model.EvalObjectLog;
import io.github.accontra.eval.domain.model.EvalTaskLog;
import io.github.accontra.eval.infrastructure.mapper.*;
import io.github.accontra.eval.infrastructure.rag.EmbeddingService;
import io.github.accontra.eval.infrastructure.rag.QdrantVectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * H6 — 汇总结果并落库。
 * S23: 等级映射 (SCORE_RANGE) + 落库 grade 字段。
 */
public class SummarizeResultHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(SummarizeResultHandler.class);

    private final EvalTaskLogMapper taskLogMapper;
    private final EvalObjectLogMapper objectLogMapper;
    private final EvalIndicatorLogMapper indicatorLogMapper;
    private final EvalGradeMappingMapper gradeMappingMapper;
    private final EmbeddingService embeddingService;
    private final QdrantVectorService vectorIndexService;

    public SummarizeResultHandler(EvalTaskLogMapper taskLogMapper,
                                  EvalObjectLogMapper objectLogMapper,
                                  EvalIndicatorLogMapper indicatorLogMapper,
                                  EvalGradeMappingMapper gradeMappingMapper,
                                  EmbeddingService embeddingService,
                                  QdrantVectorService vectorIndexService) {
        this.taskLogMapper = taskLogMapper;
        this.objectLogMapper = objectLogMapper;
        this.indicatorLogMapper = indicatorLogMapper;
        this.gradeMappingMapper = gradeMappingMapper;
        this.embeddingService = embeddingService;
        this.vectorIndexService = vectorIndexService;
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

        // S23: 等级映射
        var scene = ctx.getScene();
        String grade = computeGrade(scene != null ? scene.getId() : null, totalScore);
        ctx.setGrade(grade);

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
        objectLog.setGrade(grade);
        objectLog.setStatus("SUCCESS");
        objectLog.setEnabled(1);
        objectLogMapper.insert(objectLog);

        // 3. 创建指标日志 — S14: 含双通道对比数据
        if (ctx.getRawValues() != null) {
            // 构建 diff 索引: indexCode → IndicatorDiff
            var diffs = ctx.getIndicatorDiffs();
            Map<String, DualChannelScoringService.IndicatorDiff> diffMap = null;
            if (diffs != null) {
                diffMap = diffs.stream()
                        .collect(Collectors.toMap(
                                DualChannelScoringService.IndicatorDiff::indexCode,
                                Function.identity(),
                                (a, b) -> a));
            }

            int sn = 0;
            for (var entry : ctx.getRawValues().entrySet()) {
                String indexCode = entry.getKey();
                EvalIndicatorLog il = new EvalIndicatorLog();
                il.setObjectLogId(objectLog.getId());
                il.setTaskLogId(taskLog.getId());
                il.setIndexCode(indexCode);
                il.setClazz("INDEX");
                il.setDataValue(entry.getValue() != null ? entry.getValue().toString() : null);
                il.setSn(++sn);
                il.setEnabled(1);

                // S14: 双通道对比数据
                var diff = diffMap != null ? diffMap.get(indexCode) : null;
                if (diff != null) {
                    il.setLlmScore(diff.llmScore());
                    il.setRuleScore(diff.ruleScore());
                    il.setScoreDiff(diff.diff());
                    il.setDiffLevel(diff.diffLevel());
                    il.setLlmReason(diff.llmReason());
                    il.setScoreMode("LLM"); // 主通道
                    il.setScore(diff.llmScore()); // 实际采用 LLM 分数
                }

                indicatorLogMapper.insert(il);

                // A3 RAG: 增量更新向量索引
                if (embeddingService != null && embeddingService.isAvailable()
                        && vectorIndexService != null && vectorIndexService.isAvailable()
                        && il.getLlmReason() != null && !il.getLlmReason().isBlank()) {
                    try {
                        String text = String.format("指标:%s 名称:%s 实际值:%s 打分理由:%s",
                                il.getIndexCode() != null ? il.getIndexCode() : "",
                                il.getIndexName() != null ? il.getIndexName() : "",
                                il.getDataValue() != null ? il.getDataValue() : "",
                                il.getLlmReason());
                        float[] vec = embeddingService.encode(text);
                        JSONObject payload = new JSONObject();
                        payload.set("indexCode", il.getIndexCode());
                        payload.set("indexName", il.getIndexName());
                        payload.set("dataValue", il.getDataValue());
                        payload.set("llmScore", il.getLlmScore());
                        payload.set("llmReason", il.getLlmReason());
                        payload.set("diffLevel", il.getDiffLevel());
                        vectorIndexService.upsert(List.of(
                                new QdrantVectorService.Point(il.getId(), vec, payload)));
                    } catch (Exception e) {
                        log.debug("[RAG] 增量索引失败 indexCode={}: {}", il.getIndexCode(), e.getMessage());
                    }
                }
            }
        }

        log.info("[H6] 落库完成: taskLog={}, objectLog={}, totalScore={}, riskLevel={}, grade={}",
                taskLog.getId(), objectLog.getId(), totalScore, riskLevel, grade);
    }

    /** SCORE_RANGE 等级映射 */
    private String computeGrade(Long sceneId, BigDecimal totalScore) {
        if (sceneId == null) return "N/A";
        var qw = new LambdaQueryWrapper<EvalGradeMapping>()
                .eq(EvalGradeMapping::getSceneId, sceneId)
                .le(EvalGradeMapping::getLowerBound, totalScore)
                .gt(EvalGradeMapping::getUpperBound, totalScore)
                .orderByAsc(EvalGradeMapping::getPriority)
                .last("LIMIT 1");
        var gm = gradeMappingMapper.selectOne(qw);
        return gm != null ? gm.getGrade() : "N/A";
    }
}
