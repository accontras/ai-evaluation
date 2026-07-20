package io.github.accontra.eval.application.pipeline;

import io.github.accontra.eval.domain.model.EvalModelIndex;
import io.github.accontra.eval.domain.model.EvalModelStage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Stage 树节点 — 内存中的 Stage 树结构。
 *
 * LEAF:   挂指标，收集 leaf 层 LLM/规则分数
 * NORMAL: 聚合子 stage 得分
 * TOP:    路由命中 → 只算该分支
 */
public class StageNode {

    private final EvalModelStage stage;
    private final List<StageNode> children = new ArrayList<>();
    private final List<EvalModelIndex> indices = new ArrayList<>();  // 仅 LEAF 有效

    /** 聚合后的得分 (自底向上计算) */
    private BigDecimal score;
    /** 该节点下所有指标的加权得分明细: indexCode → score */
    private java.util.Map<String, BigDecimal> indicatorScores;

    public StageNode(EvalModelStage stage) {
        this.stage = stage;
    }

    public EvalModelStage getStage() { return stage; }
    public List<StageNode> getChildren() { return children; }
    public List<EvalModelIndex> getIndices() { return indices; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal v) { score = v; }
    public java.util.Map<String, BigDecimal> getIndicatorScores() { return indicatorScores; }
    public void setIndicatorScores(java.util.Map<String, BigDecimal> v) { indicatorScores = v; }

    public boolean isLeaf() { return "LEAF".equals(stage.getType()); }
    public boolean isTop() { return "TOP".equals(stage.getType()); }
    public boolean isRoot() { return stage.getParentId() == null || stage.getParentId() == 0; }

    @Override
    public String toString() {
        return "StageNode{code=" + stage.getCode() + ", type=" + stage.getType()
                + ", children=" + children.size() + ", indices=" + indices.size()
                + ", score=" + score + "}";
    }
}
