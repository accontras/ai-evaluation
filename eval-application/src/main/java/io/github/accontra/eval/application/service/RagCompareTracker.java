package io.github.accontra.eval.application.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A3.3 RAG 影子模式: 向量检索 vs 规则检索 对比追踪。
 *
 * 每次评估时记录双跑结果，用于量化对比。
 */
@Component
public class RagCompareTracker {

    private final AtomicLong totalCompare = new AtomicLong(0);
    private final AtomicLong bothFound = new AtomicLong(0);
    private final AtomicLong vectorOnly = new AtomicLong(0);
    private final AtomicLong ruleOnly = new AtomicLong(0);
    private final AtomicLong neither = new AtomicLong(0);

    /** 最近 50 条对比记录 */
    private final List<Map<String, Object>> recent = new ArrayList<>();
    private static final int MAX_RECENT = 50;

    /** 记录一次对比 */
    public void record(String bizId, boolean vectorHas, boolean ruleHas,
                       int vectorChars, int ruleChars) {
        totalCompare.incrementAndGet();
        if (vectorHas && ruleHas) bothFound.incrementAndGet();
        else if (vectorHas) vectorOnly.incrementAndGet();
        else if (ruleHas) ruleOnly.incrementAndGet();
        else neither.incrementAndGet();

        // 保留最近记录
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
}
