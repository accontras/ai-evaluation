package io.github.accontra.eval.infrastructure.rag;

import io.github.accontra.eval.domain.model.EvalIndicatorLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A3 RAG: 语义检索服务。
 *
 * 整合 EmbeddingService + VectorIndexService，
 * 接收查询文本 → embedding → KNN 搜索 → 返回相似案例列表。
 *
 * 当 RAG 模型不可用时自动返回空列表（调用方降级到 SimilarCaseService）。
 */
@Component
public class VectorRagService {

    private static final Logger log = LoggerFactory.getLogger(VectorRagService.class);

    private final EmbeddingService embeddingService;
    private final VectorIndexService vectorIndexService;

    public VectorRagService(EmbeddingService embeddingService,
                            VectorIndexService vectorIndexService) {
        this.embeddingService = embeddingService;
        this.vectorIndexService = vectorIndexService;
    }

    public boolean isAvailable() {
        return embeddingService.isAvailable() && vectorIndexService.isAvailable();
    }

    /**
     * 检索与当前指标语义最相似的 K 个历史案例。
     *
     * @param indexCode  指标编码
     * @param indexName  指标名称
     * @param dataValue  实际值
     * @param k          返回数量
     * @return 相似案例列表（空列表表示不可用或无结果）
     */
    public List<SimilarCase> search(String indexCode, String indexName,
                                    String dataValue, int k) {
        if (!isAvailable()) {
            log.trace("[RAG] 服务不可用, 返回空列表");
            return List.of();
        }

        // 拼接查询文本: 语义信息越丰富检索越准
        String queryText = String.format("指标:%s 名称:%s 实际值:%s",
                indexCode != null ? indexCode : "",
                indexName != null ? indexName : "",
                dataValue != null ? dataValue : "");

        long t1 = System.currentTimeMillis();
        float[] queryVec = embeddingService.encode(queryText);
        long t2 = System.currentTimeMillis();

        var results = vectorIndexService.search(queryVec, k);
        long t3 = System.currentTimeMillis();

        if (!results.isEmpty()) {
            log.debug("[RAG] encode={}ms, search={}ms, hits={}",
                    (t2 - t1), (t3 - t2), results.size());
        }

        return results.stream()
                .map(r -> {
                    var logEntry = new EvalIndicatorLog();
                    logEntry.setId(r.logId());
                    logEntry.setIndexCode(r.indexCode());
                    logEntry.setIndexName(r.indexName());
                    logEntry.setDataValue(r.dataValue());
                    logEntry.setLlmReason(r.llmReason());
                    // 归一化相似度: DOT_PRODUCT score → 0~100
                    double similarity = Math.max(0, Math.min(100, r.score() * 100));
                    return new SimilarCase(logEntry, similarity);
                })
                .collect(Collectors.toList());
    }

    /** 相似案例 — 复用 SimilarCaseService 的输出契约 */
    public record SimilarCase(EvalIndicatorLog log, double similarity) {
        public String toPromptExample() {
            return String.format("%s=%.1f分, %s",
                    log.getIndexCode() != null ? log.getIndexCode() : "?",
                    log.getLlmScore() != null ? log.getLlmScore() : 0,
                    log.getLlmReason() != null ? log.getLlmReason().substring(0,
                            Math.min(60, log.getLlmReason().length())) : "");
        }
    }
}
