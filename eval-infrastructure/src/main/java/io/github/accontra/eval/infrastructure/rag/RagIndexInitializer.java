package io.github.accontra.eval.infrastructure.rag;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.accontra.eval.domain.model.EvalIndicatorLog;
import io.github.accontra.eval.infrastructure.mapper.EvalIndicatorLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A3 RAG: 历史数据迁移 — 启动时将 eval_indicator_log 批量向量化迁入 Qdrant。
 */
@Component
public class RagIndexInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RagIndexInitializer.class);

    private final EvalIndicatorLogMapper indicatorLogMapper;
    private final EmbeddingService embeddingService;
    private final QdrantVectorService qdrantVectorService;

    public RagIndexInitializer(EvalIndicatorLogMapper indicatorLogMapper,
                               EmbeddingService embeddingService,
                               QdrantVectorService qdrantVectorService) {
        this.indicatorLogMapper = indicatorLogMapper;
        this.embeddingService = embeddingService;
        this.qdrantVectorService = qdrantVectorService;
    }

    @Override
    public void run(String... args) {
        if (!embeddingService.isAvailable() || !qdrantVectorService.isAvailable()) {
            log.info("[RAG] 服务不可用, 跳过索引迁移");
            return;
        }
        // Qdrant: 每次启动全量重迁 (upsert 幂等)
        new Thread(this::migrate, "rag-index-migrate").start();
    }

    private void migrate() {
        log.info("[RAG] 开始历史数据迁移到 Qdrant...");
        long start = System.currentTimeMillis();
        long total = 0;

        try {
            var qw = new LambdaQueryWrapper<EvalIndicatorLog>()
                    .isNotNull(EvalIndicatorLog::getLlmReason)
                    .isNotNull(EvalIndicatorLog::getLlmScore)
                    .orderByAsc(EvalIndicatorLog::getId);

            int batchSize = 50;
            long offset = 0;
            List<QdrantVectorService.Point> batch = new ArrayList<>();

            while (true) {
                var page = indicatorLogMapper.selectList(
                        qw.last("LIMIT " + batchSize + " OFFSET " + offset));
                if (page == null || page.isEmpty()) break;

                for (var logEntry : page) {
                    try {
                        String text = String.format("指标:%s 名称:%s 实际值:%s 打分理由:%s",
                                logEntry.getIndexCode() != null ? logEntry.getIndexCode() : "",
                                logEntry.getIndexName() != null ? logEntry.getIndexName() : "",
                                logEntry.getDataValue() != null ? logEntry.getDataValue() : "",
                                logEntry.getLlmReason() != null ? logEntry.getLlmReason() : "");
                        float[] vec = embeddingService.encode(text);

                        JSONObject payload = new JSONObject();
                        payload.set("indexCode", logEntry.getIndexCode());
                        payload.set("indexName", logEntry.getIndexName());
                        payload.set("dataValue", logEntry.getDataValue());
                        payload.set("llmScore", logEntry.getLlmScore());
                        payload.set("llmReason", logEntry.getLlmReason());
                        payload.set("diffLevel", logEntry.getDiffLevel());

                        batch.add(new QdrantVectorService.Point(logEntry.getId(), vec, payload));
                        total++;
                    } catch (Exception e) {
                        log.warn("[RAG] 迁移失败 id={}: {}", logEntry.getId(), e.getMessage());
                    }
                }
                offset += batchSize;
            }
            // 批量写入
            if (!batch.isEmpty()) {
                qdrantVectorService.upsert(batch);
            }
        } catch (Exception e) {
            log.error("[RAG] 迁移中断", e);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[RAG] 历史数据迁移到 Qdrant 完成: {} 条, 耗时={}ms", total, elapsed);
    }
}
