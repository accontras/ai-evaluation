package io.github.accontra.eval.application.service;

import io.github.accontra.eval.domain.model.EvalRagCompareLog;
import io.github.accontra.eval.infrastructure.mapper.EvalRagCompareLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A3.3 RAG 影子模式: 向量检索 vs 规则检索 对比追踪。
 *
 * 每次评估时记录双跑结果，用于量化对比。
 * v2: 增加 DB 持久化写入（eval_rag_compare_log 表）。
 */
@Component
public class RagCompareTracker {

    private static final Logger log = LoggerFactory.getLogger(RagCompareTracker.class);

    private final AtomicLong totalCompare = new AtomicLong(0);
    private final AtomicLong bothFound = new AtomicLong(0);
    private final AtomicLong vectorOnly = new AtomicLong(0);
    private final AtomicLong ruleOnly = new AtomicLong(0);
    private final AtomicLong neither = new AtomicLong(0);

    /** 最近 50 条对比记录 */
    private final List<Map<String, Object>> recent = new ArrayList<>();
    private static final int MAX_RECENT = 50;

    private final EvalRagCompareLogMapper mapper;

    public RagCompareTracker(EvalRagCompareLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @deprecated 使用完整签名的 {@link #record(String, String, String, String, String, List, List, List, boolean, boolean, List)}
     */
    @Deprecated
    public void record(String bizId, boolean vectorHas, boolean ruleHas,
                       int vectorChars, int ruleChars) {
        totalCompare.incrementAndGet();
        if (vectorHas && ruleHas) bothFound.incrementAndGet();
        else if (vectorHas) vectorOnly.incrementAndGet();
        else if (ruleHas) ruleOnly.incrementAndGet();
        else neither.incrementAndGet();

        synchronized (recent) {
            recent.add(Map.of(
                    "bizId", bizId,
                    "vectorHas", vectorHas,
                    "ruleHas", ruleHas,
                    "vectorChars", vectorChars,
                    "ruleChars", ruleChars,
                    "time", System.currentTimeMillis()));
            if (recent.size() > MAX_RECENT) {
                recent.subList(0, recent.size() - MAX_RECENT).clear();
            }
        }

        // DB 写入（简化版，仅基本信息）
        try {
            EvalRagCompareLog dbLog = new EvalRagCompareLog();
            dbLog.setBizId(bizId);
            dbLog.setVectorHit(vectorHas ? 1 : 0);
            dbLog.setRuleHit(ruleHas ? 1 : 0);
            dbLog.setCreatedAt(LocalDateTime.now());
            mapper.insert(dbLog);
        } catch (Exception e) {
            log.debug("[RagCompare] DB 写入失败 (非致命): {}", e.getMessage());
        }
    }

    /** 完整字段记录（用于 RagQualityEvalRunner 跑批评测） */
    public void record(String bizId, String sceneCode, String indexCode,
                       String indexName, String dataValue,
                       List<Long> vectorIds, List<Long> ruleIds,
                       List<Double> similarities,
                       boolean vectorHas, boolean ruleHas,
                       List<Integer> groundTruthRel) {
        totalCompare.incrementAndGet();
        if (vectorHas && ruleHas) bothFound.incrementAndGet();
        else if (vectorHas) vectorOnly.incrementAndGet();
        else if (ruleHas) ruleOnly.incrementAndGet();
        else neither.incrementAndGet();

        synchronized (recent) {
            recent.add(Map.of(
                    "bizId", bizId != null ? bizId : "",
                    "vectorHas", vectorHas,
                    "ruleHas", ruleHas,
                    "vectorCount", vectorIds != null ? vectorIds.size() : 0,
                    "ruleCount", ruleIds != null ? ruleIds.size() : 0,
                    "time", System.currentTimeMillis()));
            if (recent.size() > MAX_RECENT) {
                recent.subList(0, recent.size() - MAX_RECENT).clear();
            }
        }

        // DB 写入（完整字段）
        try {
            EvalRagCompareLog dbLog = new EvalRagCompareLog();
            dbLog.setBizId(bizId);
            dbLog.setSceneCode(sceneCode);
            dbLog.setIndexCode(indexCode);
            dbLog.setIndexName(indexName);
            dbLog.setDataValue(dataValue);
            dbLog.setVectorResults(toJson(vectorIds));
            dbLog.setRuleResults(toJson(ruleIds));
            dbLog.setVectorSimilarities(toJson(similarities));
            dbLog.setVectorHit(vectorHas ? 1 : 0);
            dbLog.setRuleHit(ruleHas ? 1 : 0);
            dbLog.setGroundTruthRel(toJson(groundTruthRel));
            dbLog.setCreatedAt(LocalDateTime.now());
            mapper.insert(dbLog);
        } catch (Exception e) {
            log.warn("[RagCompare] DB 写入失败: {}", e.getMessage());
        }
    }

    /** 获取对比统计 */
    public Map<String, Object> getStats() {
        long total = totalCompare.get();
        return Map.of(
                "totalCompares", total,
                "bothFound", bothFound.get(),
                "vectorOnly", vectorOnly.get(),
                "ruleOnly", ruleOnly.get(),
                "neither", neither.get(),
                "vectorHitRate", total > 0
                        ? String.format("%.1f%%", 100.0 * (bothFound.get() + vectorOnly.get()) / total)
                        : "N/A",
                "ruleHitRate", total > 0
                        ? String.format("%.1f%%", 100.0 * (bothFound.get() + ruleOnly.get()) / total)
                        : "N/A",
                "recent", getRecent()
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getRecent() {
        synchronized (recent) {
            return new ArrayList<>(recent);
        }
    }

    /** List → JSON 字符串（hutool JSONUtil 不可用时用 toString） */
    private String toJson(List<?> list) {
        if (list == null) return null;
        try {
            return cn.hutool.json.JSONUtil.toJsonStr(list);
        } catch (NoClassDefFoundError | Exception e) {
            return list.toString();
        }
    }
}
