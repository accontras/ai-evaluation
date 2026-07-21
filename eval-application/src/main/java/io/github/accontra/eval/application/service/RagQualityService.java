package io.github.accontra.eval.application.service;

import java.util.*;

/**
 * A3.3 检索质量指标计算：HR@K 和 NDCG@K。
 *
 * 纯计算，无状态，无外部依赖，可直接单元测试。
 * 相关性使用二元制（1=相关, 0=不相关）。
 */
public class RagQualityService {

    /**
     * 单条查询的检索结果。
     *
     * @param groundTruthRel 并行数组：与检索结果一一对应，1=相关, 0=不相关
     */
    public record QueryResult(List<Integer> groundTruthRel) {}

    // ============ Hit Rate@K ============

    /**
     * 计算 Hit Rate@K。
     *
     * HR@K = Top-K 中至少有一个相关文档的查询占比。
     *
     * @param results 所有查询的检索结果
     * @param kValues K 的取值（如 1, 3, 5）
     * @return Map<K, HR>
     */
    public Map<Integer, Double> hrAtK(List<QueryResult> results, int... kValues) {
        Map<Integer, Double> hr = new LinkedHashMap<>();
        if (results.isEmpty()) return hr;

        for (int k : kValues) {
            long hitCount = 0;
            for (QueryResult qr : results) {
                int limit = Math.min(k, qr.groundTruthRel().size());
                for (int i = 0; i < limit; i++) {
                    if (qr.groundTruthRel().get(i) == 1) {
                        hitCount++;
                        break;
                    }
                }
            }
            hr.put(k, (double) hitCount / results.size());
        }
        return hr;
    }

    // ============ NDCG@K ============

    /**
     * 计算 NDCG@K（二元相关）。
     *
     * NDCG = DCG / IDCG，折损因子 = log₂(rank+1)。
     *
     * 边界情况：DCG=0 且 IDCG=0 → NDCG=1.0（没有该返回的都没返回）。
     *
     * @param results 所有查询的检索结果
     * @param kValues K 的取值
     * @return Map<K, NDCG>
     */
    public Map<Integer, Double> ndcgAtK(List<QueryResult> results, int... kValues) {
        Map<Integer, Double> ndcg = new LinkedHashMap<>();
        if (results.isEmpty()) return ndcg;

        for (int k : kValues) {
            double sum = 0.0;
            for (QueryResult qr : results) {
                int limit = Math.min(k, qr.groundTruthRel().size());
                double dcg = dcg(qr.groundTruthRel(), limit);
                double idcg = idcg(qr.groundTruthRel(), limit);
                sum += (dcg == 0.0 && idcg == 0.0) ? 1.0 : dcg / idcg;
            }
            ndcg.put(k, sum / results.size());
        }
        return ndcg;
    }

    /** DCG@K = Σ rel_i / log₂(i+1), i 从 0 开始 */
    private double dcg(List<Integer> rel, int limit) {
        double dcg = 0.0;
        for (int i = 0; i < limit && i < rel.size(); i++) {
            if (rel.get(i) == 1) {
                dcg += 1.0 / log2(i + 2);  // rank = i+1, 折损因子 = log₂(rank+1)
            }
        }
        return dcg;
    }

    /** IDCG@K = 理想排序下的 DCG（所有相关文档排最前） */
    private double idcg(List<Integer> rel, int limit) {
        int totalRelevant = 0;
        int effectiveLimit = Math.min(limit, rel.size());
        for (int i = 0; i < effectiveLimit; i++) {
            if (rel.get(i) == 1) totalRelevant++;
        }
        double idcg = 0.0;
        for (int i = 0; i < totalRelevant; i++) {
            idcg += 1.0 / log2(i + 2);
        }
        return idcg;
    }

    /** log₂(x) */
    private double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
