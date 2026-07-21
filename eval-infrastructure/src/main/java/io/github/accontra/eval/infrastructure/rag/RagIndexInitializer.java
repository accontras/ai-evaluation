package io.github.accontra.eval.infrastructure.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.accontra.eval.domain.model.EvalIndicatorLog;
import io.github.accontra.eval.infrastructure.mapper.EvalIndicatorLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * A3 RAG: 历史数据迁移 — 启动时将 eval_indicator_log 批量向量化建索引。
 *
 * 只在索引为空时执行全量迁移；已有数据时跳过。
 * 迁移在后台异步执行，不阻塞应用启动。
 */
@Component
public class RagIndexInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RagIndexInitializer.class);

    private final EvalIndicatorLogMapper indicatorLogMapper;
    private final EmbeddingService embeddingService;
    private final VectorIndexService vectorIndexService;

    public RagIndexInitializer(EvalIndicatorLogMapper indicatorLogMapper,
                               EmbeddingService embeddingService,
                               VectorIndexService vectorIndexService) {
        this.indicatorLogMapper = indicatorLogMapper;
        this.embeddingService = embeddingService;
        this.vectorIndexService = vectorIndexService;
    }

    @Override
    public void run(String... args) {
        if (!embeddingService.isAvailable() || !vectorIndexService.isAvailable()) {
            log.info("[RAG] 服务不可用, 跳过索引迁移");
            return;
        }

        // 已有索引数据则跳过全量迁移
        if (vectorIndexService.docCount() > 0) {
            log.info("[RAG] 索引已有 {} 条数据, 跳过全量迁移", vectorIndexService.docCount());
            return;
        }

        // 异步执行, 不阻塞启动
        new Thread(this::migrate, "rag-index-migrate").start();
    }

    private void migrate() {
        log.info("[RAG] 开始历史数据迁移...");
        long start = System.currentTimeMillis();
        long total = 0;

        try {
            var qw = new LambdaQueryWrapper<EvalIndicatorLog>()
                    .isNotNull(EvalIndicatorLog::getLlmReason)
                    .isNotNull(EvalIndicatorLog::getLlmScore)
                    .orderByAsc(EvalIndicatorLog::getId);

            int batchSize = 50;
            long offset = 0;

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
                        vectorIndexService.addDocument(logEntry.getId(), vec,
                                logEntry.getIndexCode(), logEntry.getIndexName(),
                                logEntry.getDataValue(), logEntry.getLlmReason());
                        total++;
                    } catch (Exception e) {
                        log.warn("[RAG] 迁移失败 id={}: {}", logEntry.getId(), e.getMessage());
                    }
                }
                offset += batchSize;
            }
        } catch (Exception e) {
            log.error("[RAG] 迁移中断", e);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[RAG] 历史数据迁移完成: {} 条, 耗时={}ms", total, elapsed);
    }
}
