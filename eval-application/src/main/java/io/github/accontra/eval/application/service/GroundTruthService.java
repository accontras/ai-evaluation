package io.github.accontra.eval.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * A3.3 Ground Truth 标注数据读写服务。
 *
 * 操作 data/rag-ground-truth.json 文件，支持全量读取和按 ID 更新。
 */
@Component
public class GroundTruthService {

    private static final Logger log = LoggerFactory.getLogger(GroundTruthService.class);

    private final ObjectMapper objectMapper;
    private final Path filePath;

    public GroundTruthService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.filePath = Paths.get("data", "rag-ground-truth.json");
    }

    /** 加载全部 ground truth 条目 */
    public List<GroundTruthEntry> listAll() {
        try {
            if (!Files.exists(filePath)) {
                log.warn("[GroundTruth] 文件不存在: {}", filePath.toAbsolutePath());
                return List.of();
            }
            return objectMapper.readValue(filePath.toFile(),
                    new TypeReference<List<GroundTruthEntry>>() {});
        } catch (IOException e) {
            log.error("[GroundTruth] 读取失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按 ID 查找单条 */
    public GroundTruthEntry get(String id) {
        return listAll().stream()
                .filter(e -> e.id().equals(id))
                .findFirst().orElse(null);
    }

    /** 更新单条（按 ID 匹配），写回文件 */
    public GroundTruthEntry update(String id, GroundTruthEntry updated) {
        List<GroundTruthEntry> all = new ArrayList<>(listAll());
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(id)) {
                all.set(i, updated);
                break;
            }
        }
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), all);
            log.info("[GroundTruth] 已更新: id={}, annotator={}", id, updated.annotator());
            return updated;
        } catch (IOException e) {
            log.error("[GroundTruth] 写入失败: {}", e.getMessage());
            return null;
        }
    }

    /** Ground truth JSON 条目 */
    public record GroundTruthEntry(
            String id, String annotator,
            String indexCode, String indexName,
            String dataValue, List<Long> relevantLogIds, String notes) {}
}
