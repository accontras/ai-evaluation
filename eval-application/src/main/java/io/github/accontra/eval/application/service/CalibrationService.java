package io.github.accontra.eval.application.service;

import cn.hutool.json.JSONObject;
import io.github.accontra.eval.domain.model.EvalIndicatorLog;
import io.github.accontra.eval.infrastructure.mapper.EvalIndicatorLogMapper;
import io.github.accontra.eval.infrastructure.rag.EmbeddingService;
import io.github.accontra.eval.infrastructure.rag.QdrantVectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 标定服务 — 将人工确认过的评估结果向量化入库。
 *
 * 独立于评估 Pipeline，仅在被显式调用时执行。
 * 入库条件：indicator_log 存在 + llmReason 非空。
 * 调用方应确保只有经过人工审核的记录才提交标定。
 */
@Service
public class CalibrationService {

    private static final Logger log = LoggerFactory.getLogger(CalibrationService.class);

    private final EvalIndicatorLogMapper indicatorLogMapper;
    private final EmbeddingService embeddingService;
    private final QdrantVectorService qdrantVectorService;

    public CalibrationService(EvalIndicatorLogMapper indicatorLogMapper,
                               EmbeddingService embeddingService,
                               QdrantVectorService qdrantVectorService) {
        this.indicatorLogMapper = indicatorLogMapper;
        this.embeddingService = embeddingService;
        this.qdrantVectorService = qdrantVectorService;
    }

    /**
     * 对标定的指标记录执行向量化入库。
     *
     * @param ids 待标定的 EvalIndicatorLog ID 列表
     * @return 标定结果（总数、成功数、失败数、逐条明细）
     */
    public CalibrationResult calibrate(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new CalibrationResult(0, 0, 0, List.of());
        }

        if (!embeddingService.isAvailable()) {
            log.warn("[Calibration] Embedding 服务不可用，全部标定失败");
            List<CalibrationDetail> details = ids.stream()
                    .map(id -> new CalibrationDetail(id, "FAILED", "Embedding 服务不可用"))
                    .toList();
            return new CalibrationResult(ids.size(), 0, ids.size(), details);
        }

        if (!qdrantVectorService.isAvailable()) {
            log.warn("[Calibration] Qdrant 服务不可用，全部标定失败");
            List<CalibrationDetail> details = ids.stream()
                    .map(id -> new CalibrationDetail(id, "FAILED", "Qdrant 服务不可用"))
                    .toList();
            return new CalibrationResult(ids.size(), 0, ids.size(), details);
        }

        List<CalibrationDetail> details = new ArrayList<>();
        int success = 0;
        int failed = 0;

        for (Long id : ids) {
            try {
                EvalIndicatorLog il = indicatorLogMapper.selectById(id);
                if (il == null) {
                    details.add(new CalibrationDetail(id, "FAILED", "记录不存在"));
                    failed++;
                    continue;
                }

                if (il.getLlmReason() == null || il.getLlmReason().isBlank()) {
                    details.add(new CalibrationDetail(id, "FAILED", "缺少 LLM 打分理由，无法标定"));
                    failed++;
                    continue;
                }

                // 拼接文本 → embedding
                String text = String.format("指标:%s 名称:%s 实际值:%s 打分理由:%s",
                        il.getIndexCode() != null ? il.getIndexCode() : "",
                        il.getIndexName() != null ? il.getIndexName() : "",
                        il.getDataValue() != null ? il.getDataValue() : "",
                        il.getLlmReason());
                float[] vec = embeddingService.encode(text);

                // 构建 payload → upsert Qdrant
                JSONObject payload = new JSONObject();
                payload.set("indexCode", il.getIndexCode());
                payload.set("indexName", il.getIndexName());
                payload.set("dataValue", il.getDataValue());
                payload.set("llmScore", il.getLlmScore());
                payload.set("llmReason", il.getLlmReason());
                payload.set("diffLevel", il.getDiffLevel());
                qdrantVectorService.upsert(List.of(
                        new QdrantVectorService.Point(il.getId(), vec, payload)));

                details.add(new CalibrationDetail(id, "SUCCESS", null));
                success++;
            } catch (Exception e) {
                log.debug("[Calibration] 标定失败 id={}: {}", id, e.getMessage());
                details.add(new CalibrationDetail(id, "FAILED", e.getMessage()));
                failed++;
            }
        }

        log.info("[Calibration] 标定完成: total={}, success={}, failed={}", ids.size(), success, failed);
        return new CalibrationResult(ids.size(), success, failed, details);
    }

    // ---- result types ----

    public record CalibrationResult(int total, int success, int failed, List<CalibrationDetail> details) {}

    public record CalibrationDetail(Long id, String status, String error) {}
}
