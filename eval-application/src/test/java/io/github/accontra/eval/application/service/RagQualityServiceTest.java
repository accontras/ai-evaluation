package io.github.accontra.eval.application.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RagQualityService 单元测试 — HR@K 和 NDCG@K 计算。
 */
class RagQualityServiceTest {

    private final RagQualityService svc = new RagQualityService();

    // ============ HR@K ============

    @Test
    void hrAtK_allHit() {
        var results = List.of(
                qr(1, 0, 0),
                qr(1, 0, 0),
                qr(0, 0, 1)
        );

        Map<Integer, Double> hr = svc.hrAtK(results, 1, 3);

        assertEquals(2.0 / 3.0, hr.get(1), 0.001);
        assertEquals(1.0, hr.get(3), 0.001);
    }

    @Test
    void hrAtK_allMiss() {
        var results = List.of(qr(0, 0, 0), qr(0, 0, 0));

        Map<Integer, Double> hr = svc.hrAtK(results, 1, 3);

        assertEquals(0.0, hr.get(1), 0.001);
        assertEquals(0.0, hr.get(3), 0.001);
    }

    @Test
    void hrAtK_emptyInput() {
        Map<Integer, Double> hr = svc.hrAtK(Collections.emptyList(), 1, 3);
        assertTrue(hr.isEmpty());
    }

    // ============ NDCG@K ============

    @Test
    void ndcgAtK_perfectOrder() {
        var results = List.of(qr(1, 1, 1));

        Map<Integer, Double> ndcg = svc.ndcgAtK(results, 3);

        assertEquals(1.0, ndcg.get(3), 0.001);
    }

    @Test
    void ndcgAtK_suboptimalOrder() {
        var results = List.of(qr(0, 1, 0));

        Map<Integer, Double> ndcg = svc.ndcgAtK(results, 3);

        assertTrue(ndcg.get(3) > 0.5 && ndcg.get(3) < 0.8,
                () -> "suboptimal: NDCG@3 ≈ 0.63, got " + ndcg.get(3));
    }

    @Test
    void ndcgAtK_allIrrelevant() {
        var results = List.of(qr(0, 0, 0));

        Map<Integer, Double> ndcg = svc.ndcgAtK(results, 3);

        assertEquals(1.0, ndcg.get(3), 0.001);
    }

    @Test
    void ndcgAtK_multiQuery() {
        // 两条查询的 NDCG@3 平均值
        var results = List.of(
                qr(1, 1, 0),   // rel=[1,1,0]: DCG=1.63, IDCG=1.63 → NDCG=1.0
                qr(0, 1, 0)    // rel=[0,1,0]: DCG=0.63, IDCG=1.0 → NDCG=0.63
        );

        Map<Integer, Double> ndcg = svc.ndcgAtK(results, 3);

        // 平均值 = (1.0 + 0.63) / 2 ≈ 0.815
        assertTrue(ndcg.get(3) > 0.7 && ndcg.get(3) < 0.9,
                () -> "multi-query avg ≈ 0.815, got " + ndcg.get(3));
    }

    @Test
    void ndcgAtK_kLargerThanResults() {
        var results = List.of(qr(0, 1));

        Map<Integer, Double> ndcg = svc.ndcgAtK(results, 5);

        assertTrue(ndcg.get(5) > 0.5 && ndcg.get(5) < 0.8,
                () -> "K > results: NDCG@5 ≈ 0.63, got " + ndcg.get(5));
    }

    // ============ helper ============

    private static RagQualityService.QueryResult qr(Integer... rel) {
        return new RagQualityService.QueryResult(Arrays.asList(rel));
    }
}
