package io.github.accontra.eval.application.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RagQualityService 单元测试 — HR@K 和 NDCG@K 计算。
 * TDD RED phase: 测试先写，RagQualityService 还不存在，编译预期失败。
 */
class RagQualityServiceTest {

    private final RagQualityService svc = new RagQualityService();

    // ============ HR@K ============

    @Test
    void hrAtK_allHit() {
        // 3 条查询：前 2 条 Top-1 命中，第 3 条未命中，但 Top-3 全命中
        var results = List.of(
                qr(1, 0, 0),   // Top-1 命中
                qr(1, 0, 0),   // Top-1 命中
                qr(0, 0, 1)    // Top-1 不中，Top-3 中
        );

        Map<Integer, Double> hr = svc.hrAtK(results, 1, 3);

        assertEquals(2.0 / 3.0, hr.get(1), 0.001, "HR@1: 2/3 命中");
        assertEquals(1.0, hr.get(3), 0.001, "HR@3: 全命中");
    }

    @Test
    void hrAtK_allMiss() {
        var results = List.of(
                qr(0, 0, 0),
                qr(0, 0, 0)
        );

        Map<Integer, Double> hr = svc.hrAtK(results, 1, 3);

        assertEquals(0.0, hr.get(1), 0.001);
        assertEquals(0.0, hr.get(3), 0.001);
    }

    @Test
    void hrAtK_emptyInput() {
        Map<Integer, Double> hr = svc.hrAtK(Collections.emptyList(), 1, 3);
        assertTrue(hr.isEmpty(), "空输入应返回空 Map");
    }

    // ============ NDCG@K ============

    @Test
    void ndcgAtK_perfectOrder() {
        // 最佳排序：3 个相关文档全在最前面
        var results = List.of(qr(1, 1, 1));

        Map<Integer, Double> ndcg = svc.ndcgAtK(results, 3);

        assertEquals(1.0, ndcg.get(3), 0.001, "perfect order → NDCG@3=1.0");
    }

    @Test
    void ndcgAtK_suboptimalOrder() {
        // 相关文档在位置 2，不在位置 1
        var results = List.of(qr(0, 1, 0));

        Map<Integer, Double> ndcg = svc.ndcgAtK(results, 3);

        // rel=[0,1,0], IDCG 假设最多 1 个相关可排第一
        // DCG@3 = 0/log₂(2) + 1/log₂(3) + 0/log₂(4) = 0 + 0.6309 + 0 = 0.6309
        // IDCG@3 (1个相关排第一) = 1/log₂(2) = 1.0
        // NDCG@3 = 0.6309
        assertTrue(ndcg.get(3) > 0.5 && ndcg.get(3) < 0.8,
                "suboptimal: NDCG@3 ≈ 0.63, got " + ndcg.get(3));
    }

    @Test
    void ndcgAtK_allIrrelevant() {
        // 全不相关：DCG=0, IDCG=0 → NDCG=1.0（没有该返回的都没返回 = 完美）
        var results = List.of(qr(0, 0, 0));

        Map<Integer, Double> ndcg = svc.ndcgAtK(results, 3);

        assertEquals(1.0, ndcg.get(3), 0.001, "DCG=0 && IDCG=0 → NDCG=1.0");
    }

    @Test
    void ndcgAtK_multiQuery() {
        var results = List.of(
                qr(1, 1, 0),   // NDCG@3 < 1.0
                qr(1, 0, 0)    // NDCG@3 < 1.0 but > 0
        );

        Map<Integer, Double> ndcg = svc.ndcgAtK(results, 3);

        // 两条查询的 NDCG 平均值，应该在 0.5-1.0 之间
        assertTrue(ndcg.get(3) > 0.4 && ndcg.get(3) < 1.0,
                "multi-query avg should be between 0.5 and 1.0, got " + ndcg.get(3));
    }

    @Test
    void ndcgAtK_kLargerThanResults() {
        // K=5 但只有 2 个结果，只计算实际排名
        var results = List.of(qr(0, 1));  // 只有 2 个

        Map<Integer, Double> ndcg = svc.ndcgAtK(results, 5);

        // rel=[0,1], DCG = 0 + 1/log₂(3) = 0.6309, IDCG = 1/log₂(2) = 1.0
        // NDCG = 0.6309
        assertTrue(ndcg.get(5) > 0.5 && ndcg.get(5) < 0.8,
                "K > results: NDCG@5 ≈ 0.63, got " + ndcg.get(5));
    }

    // ============ helper ============

    /** 构建 QueryResult（groundTruthRel = 相关性并行数组） */
    private static RagQualityService.QueryResult qr(Integer... rel) {
        return new RagQualityService.QueryResult(Arrays.asList(rel));
    }
}
