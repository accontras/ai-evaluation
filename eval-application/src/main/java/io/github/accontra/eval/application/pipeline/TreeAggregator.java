package io.github.accontra.eval.application.pipeline;

import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.common.util.ExpressionUtil;
import io.github.accontra.eval.domain.model.EvalIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 树聚合器 — 自底向上聚合 Stage 树得分。
 *
 * 规则 (永不交给 LLM):
 *   LEAF:   收集子指标得分 → weighted_sum / sum / min
 *   NORMAL: 聚合子 stage 得分 → weighted_sum / sum / min
 *   TOP:    JEXL 路由匹配 → 只算命中分支
 *
 * 聚合永远由规则引擎负责——这是审计底线。
 */
public class TreeAggregator {

    private static final Logger log = LoggerFactory.getLogger(TreeAggregator.class);

    private static final String DEFAULT_MODE = "weighted_sum";

    /**
     * 聚合整棵树，返回 root score。
     */
    public BigDecimal aggregate(StageNode root,
                                Map<String, LlmScoringStrategy.ScoreResult> indicatorScores,
                                Map<String, EvalIndex> indexBaseMap,
                                String defaultAggMode) {
        return aggregate(root, indicatorScores, indexBaseMap, defaultAggMode, Map.of());
    }

    /**
     * 聚合整棵树 — 带路由变量。
     *
     * @param routingVars attrValues + rawValues，供 TOP 路由 JEXL 条件求值
     */
    public BigDecimal aggregate(StageNode root,
                                Map<String, LlmScoringStrategy.ScoreResult> indicatorScores,
                                Map<String, EvalIndex> indexBaseMap,
                                String defaultAggMode,
                                Map<String, Object> routingVars) {

        // 收集所有节点，按 level 降序 (自底向上)
        List<StageNode> allNodes = new ArrayList<>();
        collectNodes(root, allNodes);
        allNodes.sort((a, b) -> {
            int la = a.getStage().getLevel() != null ? a.getStage().getLevel() : 0;
            int lb = b.getStage().getLevel() != null ? b.getStage().getLevel() : 0;
            return Integer.compare(lb, la);
        });

        for (var node : allNodes) {
            BigDecimal nodeScore;
            if (node.isLeaf() || !node.getIndices().isEmpty()) {
                nodeScore = aggregateLeaf(node, indicatorScores, indexBaseMap);
            } else {
                nodeScore = aggregateChildren(node, defaultAggMode, routingVars);
            }
            node.setScore(nodeScore);
            log.debug("Aggregated {} ({}): score={}", node.getStage().getCode(),
                    node.getStage().getType(), nodeScore);
        }

        return root.getScore() != null ? root.getScore() : BigDecimal.ZERO;
    }

    /** LEAF 聚合 */
    private BigDecimal aggregateLeaf(StageNode node,
                                     Map<String, LlmScoringStrategy.ScoreResult> indicatorScores,
                                     Map<String, EvalIndex> indexBaseMap) {
        if (node.getIndices().isEmpty()) return defaultScore(node);

        String mode = node.getStage().getAggregateMode() != null
                ? node.getStage().getAggregateMode() : DEFAULT_MODE;
        int stageWeight = node.getStage().getWeight() != null ? node.getStage().getWeight() : 100;

        Map<String, BigDecimal> leafScores = new LinkedHashMap<>();
        BigDecimal totalWeighted = BigDecimal.ZERO;
        int totalWeight = 0;
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;
        int count = 0;

        for (var mi : node.getIndices()) {
            var ib = indexBaseMap != null ? indexBaseMap.get(String.valueOf(mi.getIndexId())) : null;
            if (ib == null) continue;

            var sr = indicatorScores != null ? indicatorScores.get(ib.getCode()) : null;
            BigDecimal score = sr != null ? sr.score() : defaultScore(node);
            count++;

            if (mi.getScoreCap() != null && score.compareTo(mi.getScoreCap()) > 0)
                score = mi.getScoreCap();
            if (mi.getScoreFloor() != null && score.compareTo(mi.getScoreFloor()) < 0)
                score = mi.getScoreFloor();

            leafScores.put(ib.getCode(), score);
            sum = sum.add(score);
            if (min == null || score.compareTo(min) < 0) min = score;

            totalWeighted = totalWeighted.add(score.multiply(BigDecimal.valueOf(stageWeight)));
            totalWeight += stageWeight;
        }

        if (count == 0) return defaultScore(node);
        node.setIndicatorScores(leafScores);

        return switch (mode) {
            case "sum" -> sum;
            case "min" -> min != null ? min : BigDecimal.ZERO;
            case "weighted_avg" -> totalWeight > 0
                    ? totalWeighted.divide(BigDecimal.valueOf(totalWeight), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            default -> totalWeight > 0
                    ? totalWeighted.divide(BigDecimal.valueOf(totalWeight), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        };
    }

    /** NORMAL/TOP 聚合 */
    private BigDecimal aggregateChildren(StageNode node, String defaultAggMode,
                                          Map<String, Object> routingVars) {
        if (node.getChildren().isEmpty()) return defaultScore(node);

        // S16: TOP 路由 — JEXL 条件匹配
        if (node.isTop()) {
            return aggregateTop(node, routingVars);
        }

        // NORMAL: 加权聚合所有子节点
        return aggregateNormal(node, defaultAggMode);
    }

    /** TOP 路由: 按 priority 排序子分支，JEXL 条件匹配，命中唯一分支 */
    private BigDecimal aggregateTop(StageNode node, Map<String, Object> routingVars) {
        // 按 priority 排序
        var candidates = node.getChildren().stream()
                .sorted(Comparator.comparing(c -> c.getStage().getPriority() != null
                        ? c.getStage().getPriority() : Integer.MAX_VALUE))
                .toList();

        for (var child : candidates) {
            String condition = child.getStage().getRouteCondition();
            if (condition == null || condition.isBlank()) continue; // 无条件跳过

            boolean matched = evalRouteCondition(condition, routingVars);
            log.info("[TOP] {} → {} routeCondition='{}' matched={}",
                    node.getStage().getCode(), child.getStage().getCode(), condition, matched);

            if (matched) {
                return child.getScore() != null ? child.getScore() : BigDecimal.ZERO;
            }
        }

        // Fallback: 第一个无条件子分支 OR 第一个子分支
        var defaultChild = candidates.stream()
                .filter(c -> c.getStage().getRouteCondition() == null
                        || c.getStage().getRouteCondition().isBlank())
                .findFirst()
                .orElse(candidates.isEmpty() ? null : candidates.get(0));

        if (defaultChild != null) {
            log.info("[TOP] {} → {} (default fallback)", node.getStage().getCode(),
                    defaultChild.getStage().getCode());
            return defaultChild.getScore() != null ? defaultChild.getScore() : BigDecimal.ZERO;
        }

        return BigDecimal.ZERO;
    }

    /** NORMAL: 加权聚合 */
    private BigDecimal aggregateNormal(StageNode node, String defaultAggMode) {
        String mode = node.getStage().getAggregateMode() != null
                ? node.getStage().getAggregateMode() : defaultAggMode;

        BigDecimal totalWeighted = BigDecimal.ZERO;
        int totalWeight = 0;
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;

        for (var child : node.getChildren()) {
            var cs = child.getScore();
            if (cs == null) continue;
            int w = child.getStage().getWeight() != null ? child.getStage().getWeight() : 100;
            totalWeighted = totalWeighted.add(cs.multiply(BigDecimal.valueOf(w)));
            totalWeight += w;
            sum = sum.add(cs);
            if (min == null || cs.compareTo(min) < 0) min = cs;
        }

        if (totalWeight == 0) return defaultScore(node);

        return switch (mode) {
            case "sum" -> sum;
            case "min" -> min != null ? min : BigDecimal.ZERO;
            default -> totalWeighted.divide(BigDecimal.valueOf(totalWeight), 2, RoundingMode.HALF_UP);
        };
    }

    /** 评估 JEXL 路由条件 */
    private boolean evalRouteCondition(String condition, Map<String, Object> routingVars) {
        try {
            return ExpressionUtil.evalBool(condition, routingVars, null);
        } catch (Exception e) {
            log.warn("TOP route JEXL eval failed: condition='{}', vars={}, error={}",
                    condition, routingVars, e.getMessage());
            return false;
        }
    }

    private BigDecimal defaultScore(StageNode node) {
        return node.getStage().getDefaultScore() != null
                ? node.getStage().getDefaultScore() : BigDecimal.valueOf(100);
    }

    private void collectNodes(StageNode node, List<StageNode> out) {
        out.add(node);
        for (var child : node.getChildren()) {
            collectNodes(child, out);
        }
    }
}
