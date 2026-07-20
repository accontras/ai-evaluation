package io.github.accontra.eval;

import io.github.accontra.eval.application.pipeline.StageNode;
import io.github.accontra.eval.application.pipeline.StageNodeAssembler;
import io.github.accontra.eval.application.pipeline.TreeAggregator;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.domain.model.EvalIndex;
import io.github.accontra.eval.domain.model.EvalModelIndex;
import io.github.accontra.eval.domain.model.EvalModelStage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S15: 树装配 + 树聚合 单元测试。
 */
class TreeAggregationTest {

    @Test
    void shouldAssembleTwoLevelTree() {
        // GIVEN: 1 root + 2 child stages + 3 indices
        var root = stage(1L, null, "COST", "normal", 1, 100, "weighted_sum");
        var child1 = stage(2L, 1L, "EFFICIENCY", "leaf", 2, 60, "weighted_sum");
        var child2 = stage(3L, 1L, "QUALITY", "leaf", 2, 40, "weighted_sum");

        var mi1 = modelIndex(1L, 2L, 1L, null, null); // idx1 → EFFICIENCY
        var mi2 = modelIndex(2L, 2L, 2L, null, null); // idx2 → EFFICIENCY
        var mi3 = modelIndex(3L, 3L, 3L, null, null); // idx3 → QUALITY

        var assembler = new StageNodeAssembler();
        var roots = assembler.assemble(
                List.of(root, child1, child2),
                List.of(mi1, mi2, mi3));

        // THEN: 1 root
        assertThat(roots).hasSize(1);
        var r = roots.get(0);
        assertThat(r.getStage().getCode()).isEqualTo("COST");
        assertThat(r.getChildren()).hasSize(2);
        assertThat(r.getChildren().get(0).getIndices()).hasSize(2); // EFFICIENCY has 2 indices
        assertThat(r.getChildren().get(1).getIndices()).hasSize(1); // QUALITY has 1 index
    }

    @Test
    void shouldAggregateWeightedTree() {
        // GIVEN: 2-level tree, scores: idx1=80, idx2=60, idx3=100
        var root = stage(1L, null, "COST", "normal", 1, 100, "weighted_sum");
        var child1 = stage(2L, 1L, "EFFICIENCY", "leaf", 2, 60, "weighted_sum");
        var child2 = stage(3L, 1L, "QUALITY", "leaf", 2, 40, "weighted_sum");

        var mi1 = modelIndex(1L, 2L, 1L, null, null);
        var mi2 = modelIndex(2L, 2L, 2L, null, null);
        var mi3 = modelIndex(3L, 3L, 3L, null, null);

        var assembler = new StageNodeAssembler();
        var treeRoot = assembler.assembleSingle(
                List.of(root, child1, child2),
                List.of(mi1, mi2, mi3));

        // 指标定义
        var idx1 = index(1L, "COST_DEV", "费用偏差率");
        var idx2 = index(2L, "ABNORM_CNT", "异常波动次数");
        var idx3 = index(3L, "FILL_RATE", "填报及时率");

        Map<String, EvalIndex> indexBaseMap = Map.of(
                "1", idx1, "2", idx2, "3", idx3);

        // 指标级分数 (来自 LLM)
        Map<String, LlmScoringStrategy.ScoreResult> scores = Map.of(
                "COST_DEV", new LlmScoringStrategy.ScoreResult("COST_DEV", "费用偏差率",
                        BigDecimal.valueOf(80), "ok"),
                "ABNORM_CNT", new LlmScoringStrategy.ScoreResult("ABNORM_CNT", "异常波动次数",
                        BigDecimal.valueOf(60), "ok"),
                "FILL_RATE", new LlmScoringStrategy.ScoreResult("FILL_RATE", "填报及时率",
                        BigDecimal.valueOf(100), "ok")
        );

        // WHEN
        var aggregator = new TreeAggregator();
        var totalScore = aggregator.aggregate(treeRoot, scores, indexBaseMap, "weighted_sum");

        // THEN
        // EFFICIENCY: (80*60 + 60*60) / (60+60) = 8400/120 = 70
        // QUALITY: (100*40) / 40 = 100
        // ROOT: (70*60 + 100*40) / (60+40) = (4200+4000)/100 = 82
        assertThat(totalScore).isEqualByComparingTo(BigDecimal.valueOf(82));
        assertThat(treeRoot.getChildren().get(0).getScore()).isEqualByComparingTo("70");
        assertThat(treeRoot.getChildren().get(1).getScore()).isEqualByComparingTo("100");
    }

    @Test
    void shouldRouteTopToMatchingBranch() {
        // GIVEN: TOP root with 2 branches
        var root = stage(1L, null, "ROOT", "TOP", 1, 100, "weighted_sum");
        var branchA = stage(2L, 1L, "R_AND_D", "leaf", 2, 60, "weighted_sum");
        branchA.setRouteCondition("attrValues[\"dept\"] == \"R&D\"");
        branchA.setPriority(1);
        var branchB = stage(3L, 1L, "DEFAULT", "leaf", 2, 40, "weighted_sum");
        branchB.setPriority(99); // default

        var mi1 = modelIndex(1L, 2L, 1L, null, null);
        var mi2 = modelIndex(2L, 3L, 2L, null, null);

        var assembler = new StageNodeAssembler();
        var treeRoot = assembler.assembleSingle(
                List.of(root, branchA, branchB), List.of(mi1, mi2));

        var idx1 = index(1L, "SCORE_A", "研发评分");
        var idx2 = index(2L, "SCORE_B", "默认评分");

        Map<String, io.github.accontra.eval.domain.model.EvalIndex> indexBaseMap =
                Map.of("1", idx1, "2", idx2);

        Map<String, LlmScoringStrategy.ScoreResult> scores = Map.of(
                "SCORE_A", sr("SCORE_A", 90),
                "SCORE_B", sr("SCORE_B", 60));

        // WHEN: dept=R&D → 命中 branchA
        var routingVars = Map.<String, Object>of("attrValues", Map.of("dept", "R&D"));
        var aggregator = new TreeAggregator();
        var totalA = aggregator.aggregate(treeRoot, scores, indexBaseMap,
                "weighted_sum", routingVars);

        // THEN: 只走 branchA = 90
        assertThat(totalA).isEqualByComparingTo("90");

        // WHEN: dept=HR → 不命中 → default branchB = 60
        var routingVars2 = Map.<String, Object>of("attrValues", Map.of("dept", "HR"));
        var totalB = aggregator.aggregate(treeRoot, scores, indexBaseMap,
                "weighted_sum", routingVars2);

        // THEN: default = 60
        assertThat(totalB).isEqualByComparingTo("60");
    }

    @Test
    void shouldHandleSingleStageAsSimpleWeighted() {
        // 单层 stage (当前 seed data 降级场景)
        var root = stage(1L, null, "COST", "normal", 1, 100, "weighted_sum");

        var mi1 = modelIndex(1L, 1L, 1L, null, null);
        var mi2 = modelIndex(2L, 1L, 2L, null, null);
        var mi3 = modelIndex(3L, 1L, 3L, null, null);

        var assembler = new StageNodeAssembler();
        var treeRoot = assembler.assembleSingle(
                List.of(root), List.of(mi1, mi2, mi3));

        var idx1 = index(1L, "A", "A");
        var idx2 = index(2L, "B", "B");
        var idx3 = index(3L, "C", "C");

        Map<String, EvalIndex> indexBaseMap = Map.of("1", idx1, "2", idx2, "3", idx3);

        Map<String, LlmScoringStrategy.ScoreResult> scores = Map.of(
                "A", sr("A", 70), "B", sr("B", 80), "C", sr("C", 90));

        var aggregator = new TreeAggregator();
        var total = aggregator.aggregate(treeRoot, scores, indexBaseMap, "weighted_sum");

        // All in same stage (weight=100), simple weighted avg = (70+80+90)/3 = 80
        assertThat(total).isEqualByComparingTo("80");
    }

    // ---- helpers ----

    private EvalModelStage stage(Long id, Long parentId, String code, String type,
                                  int level, int weight, String aggMode) {
        var s = new EvalModelStage();
        s.setId(id); s.setParentId(parentId); s.setCode(code); s.setType(type);
        s.setLevel(level); s.setWeight(weight); s.setAggregateMode(aggMode);
        s.setName(code + "_name");
        return s;
    }

    private EvalModelIndex modelIndex(Long id, Long stageId, Long indexId,
                                       java.math.BigDecimal cap, java.math.BigDecimal floor) {
        var mi = new EvalModelIndex();
        mi.setId(id); mi.setStageId(stageId); mi.setIndexId(indexId);
        mi.setScoreCap(cap); mi.setScoreFloor(floor);
        return mi;
    }

    private EvalIndex index(Long id, String code, String name) {
        var idx = new EvalIndex();
        idx.setId(id); idx.setCode(code); idx.setName(name);
        return idx;
    }

    private LlmScoringStrategy.ScoreResult sr(String code, int score) {
        return new LlmScoringStrategy.ScoreResult(code, code + "_name",
                BigDecimal.valueOf(score), "test");
    }
}
