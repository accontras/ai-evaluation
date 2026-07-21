package io.github.accontra.eval.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.accontra.eval.application.service.RagCompareTracker;
import io.github.accontra.eval.application.service.RagQualityService;
import io.github.accontra.eval.application.service.SimilarCaseService;
import io.github.accontra.eval.infrastructure.rag.VectorRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * A3.3 RAG 检索质量跑批评测脚本。
 *
 * 启动方式: mvn spring-boot:run -Dspring-boot.run.profiles=rag-eval
 * 或: java -jar eval-boot.jar --spring.profiles.active=rag-eval
 *
 * 工作流:
 *   1. 检查 RAG 服务可用性
 *   2. 加载 data/rag-ground-truth.json
 *   3. 逐条检索 (向量 + 规则) → 写入 DB
 *   4. 汇总 HR@K / NDCG@K
 *   5. 输出 results.json
 */
@Profile("rag-eval")
@Component
public class RagQualityEvalRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RagQualityEvalRunner.class);
    private static final int TOP_K = 3;
    private static final int[] K_VALUES = {1, 3, 5};

    private final VectorRagService vectorRagService;
    private final SimilarCaseService similarCaseService;
    private final RagCompareTracker ragCompareTracker;
    private final RagQualityService ragQualityService;
    private final ObjectMapper objectMapper;

    public RagQualityEvalRunner(VectorRagService vectorRagService,
                                SimilarCaseService similarCaseService,
                                RagCompareTracker ragCompareTracker,
                                ObjectMapper objectMapper) {
        this.vectorRagService = vectorRagService;
        this.similarCaseService = similarCaseService;
        this.ragCompareTracker = ragCompareTracker;
        this.ragQualityService = new RagQualityService();
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("=== RAG 检索质量评测 开始 ===");

        // Step 1: 检查可用性
        if (!vectorRagService.isAvailable()) {
            log.error("[RAG-Eval] VectorRagService 不可用, 无法执行评测");
            System.exit(1);
        }
        log.info("[RAG-Eval] 服务可用性检查通过");

        // Step 2: 加载 ground truth
        Path gtPath = Paths.get("data", "rag-ground-truth.json");
        if (!Files.exists(gtPath)) {
            log.error("[RAG-Eval] ground truth 文件不存在: {}", gtPath.toAbsolutePath());
            System.exit(1);
        }
        List<GroundTruthEntry> groundTruths = loadGroundTruth(gtPath);
        if (groundTruths.isEmpty()) {
            log.error("[RAG-Eval] ground truth 为空或格式错误");
            System.exit(1);
        }
        log.info("[RAG-Eval] 加载 {} 条标注查询", groundTruths.size());

        // Step 3: 逐条检索 + 记录
        List<RagQualityService.QueryResult> vectorQueryResults = new ArrayList<>();
        List<RagQualityService.QueryResult> ruleQueryResults = new ArrayList<>();

        for (GroundTruthEntry gt : groundTruths) {
            log.info("[RAG-Eval] 评测: {} ({} = {})", gt.id(), gt.indexCode(), gt.dataValue());
            Set<Long> relSet = new HashSet<>(gt.relevantLogIds());

            // 向量检索
            var vectorCases = vectorRagService.search(
                    gt.indexCode(), gt.indexName(), gt.dataValue(), TOP_K);
            List<Long> vectorIds = extractIds(vectorCases);
            List<Double> vectorSims = extractSimilarities(vectorCases);
            List<Integer> vectorRel = computeRelevance(vectorIds, relSet);
            boolean vectorHit = !vectorCases.isEmpty();

            // 规则检索
            var ruleCases = similarCaseService.findSimilar(
                    gt.indexCode(), gt.dataValue(), TOP_K);
            List<Long> ruleIds = extractIdsRule(ruleCases);
            List<Integer> ruleRel = computeRelevance(ruleIds, relSet);
            boolean ruleHit = !ruleCases.isEmpty();

            // 写入 DB
            ragCompareTracker.record(
                    gt.id(), "RAG-EVAL", gt.indexCode(), gt.indexName(), gt.dataValue(),
                    vectorIds, ruleIds, vectorSims,
                    vectorHit, ruleHit,
                    null  // groundTruthRel 只在 summary 阶段汇总, DB 存每条的 rel
            );

            // 收集用于指标计算
            vectorQueryResults.add(new RagQualityService.QueryResult(vectorRel));
            ruleQueryResults.add(new RagQualityService.QueryResult(ruleRel));
        }

        // Step 4: 汇总计算
        Map<Integer, Double> vectorHR = ragQualityService.hrAtK(vectorQueryResults, K_VALUES);
        Map<Integer, Double> ruleHR = ragQualityService.hrAtK(ruleQueryResults, K_VALUES);
        Map<Integer, Double> vectorNdcg = ragQualityService.ndcgAtK(vectorQueryResults, K_VALUES);
        Map<Integer, Double> ruleNdcg = ragQualityService.ndcgAtK(ruleQueryResults, K_VALUES);

        // Step 5: 输出
        Map<String, Object> report = buildReport(groundTruths.size(),
                vectorHR, ruleHR, vectorNdcg, ruleNdcg);

        Path outDir = Paths.get("data", "rag-eval-results");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("results.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outFile.toFile(), report);
        log.info("[RAG-Eval] 结果已输出: {}", outFile.toAbsolutePath());

        // 控制台打印对比表
        printSummary(vectorHR, ruleHR, vectorNdcg, ruleNdcg);

        log.info("=== RAG 检索质量评测 完成 ===");
        System.exit(0);
    }

    // ============ helpers ============

    private List<GroundTruthEntry> loadGroundTruth(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(),
                new TypeReference<List<GroundTruthEntry>>() {});
    }

    private List<Long> extractIds(List<VectorRagService.SimilarCase> cases) {
        return cases.stream().map(c -> c.log().getId()).toList();
    }

    private List<Double> extractSimilarities(List<VectorRagService.SimilarCase> cases) {
        return cases.stream().map(VectorRagService.SimilarCase::similarity).toList();
    }

    private List<Long> extractIdsRule(List<SimilarCaseService.SimilarCase> cases) {
        return cases.stream().map(c -> c.log().getId()).toList();
    }

    private List<Integer> computeRelevance(List<Long> resultIds, Set<Long> relevantIds) {
        return resultIds.stream()
                .map(id -> relevantIds.contains(id) ? 1 : 0)
                .toList();
    }

    private Map<String, Object> buildReport(int totalQueries,
                                            Map<Integer, Double> vHR, Map<Integer, Double> rHR,
                                            Map<Integer, Double> vNdcg, Map<Integer, Double> rNdcg) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalQueries", totalQueries);
        report.put("kValues", List.of(1, 3, 5));

        Map<String, Object> hr = new LinkedHashMap<>();
        hr.put("vector", vHR);
        hr.put("rule", rHR);
        report.put("hitRate", hr);

        Map<String, Object> ndcg = new LinkedHashMap<>();
        ndcg.put("vector", vNdcg);
        ndcg.put("rule", rNdcg);
        report.put("ndcg", ndcg);

        return report;
    }

    private void printSummary(Map<Integer, Double> vHR, Map<Integer, Double> rHR,
                              Map<Integer, Double> vNdcg, Map<Integer, Double> rNdcg) {
        System.out.println("\n========================================");
        System.out.println("  RAG 检索质量评测 — 双通道对比");
        System.out.println("========================================");
        System.out.printf("%-10s %12s %12s %12s%n", "指标", "K=1", "K=3", "K=5");
        System.out.println("----------------------------------------");
        printRow("向量 HR", vHR);
        printRow("规则 HR", rHR);
        printRow("向量 NDCG", vNdcg);
        printRow("规则 NDCG", rNdcg);
        System.out.println("========================================\n");
    }

    private void printRow(String label, Map<Integer, Double> data) {
        System.out.printf("%-10s %11.1f%% %11.1f%% %11.1f%%%n",
                label,
                data.getOrDefault(1, 0.0) * 100,
                data.getOrDefault(3, 0.0) * 100,
                data.getOrDefault(5, 0.0) * 100);
    }

    /** Ground truth JSON 条目 */
    public record GroundTruthEntry(
            String id, String indexCode, String indexName,
            String dataValue, List<Long> relevantLogIds, String notes) {}
}
