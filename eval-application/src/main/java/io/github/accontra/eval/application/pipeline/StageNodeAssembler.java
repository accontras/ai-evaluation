package io.github.accontra.eval.application.pipeline;

import io.github.accontra.eval.domain.model.EvalModelIndex;
import io.github.accontra.eval.domain.model.EvalModelStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Stage 树装配器 — 将扁平的 stages + indices 装配为 Stage 树/森林。
 *
 * 规则:
 *   parentId=null 或 0 → 根节点
 *   stageId 匹配 → 指标挂到对应 Stage
 *   按 level 升序保证父节点先于子节点创建
 */
public class StageNodeAssembler {

    private static final Logger log = LoggerFactory.getLogger(StageNodeAssembler.class);

    /**
     * 装配为森林 (多个根节点)，单根时取 children[0] 即可。
     */
    public List<StageNode> assemble(List<EvalModelStage> stages, List<EvalModelIndex> modelIndices) {
        if (stages == null || stages.isEmpty()) return List.of();

        // 按 level 排序
        var sorted = stages.stream()
                .sorted(Comparator.comparing(s -> s.getLevel() != null ? s.getLevel() : 0))
                .toList();

        // id → node
        Map<Long, StageNode> nodeMap = new LinkedHashMap<>();
        List<StageNode> roots = new ArrayList<>();

        for (var stage : sorted) {
            StageNode node = new StageNode(stage);
            nodeMap.put(stage.getId(), node);

            if (stage.getParentId() == null || stage.getParentId() == 0) {
                roots.add(node);
            } else {
                var parent = nodeMap.get(stage.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    log.warn("Stage {} parentId={} not found, treating as root",
                            stage.getCode(), stage.getParentId());
                    roots.add(node);
                }
            }
        }

        // 挂指标到对应 stage
        if (modelIndices != null) {
            for (var mi : modelIndices) {
                if (mi.getStageId() != null) {
                    var node = nodeMap.get(mi.getStageId());
                    if (node != null) {
                        node.getIndices().add(mi);
                    }
                }
            }
        }

        // 自动标记 leaf: 无子节点且有指标的 stage
        for (var node : nodeMap.values()) {
            if (node.getChildren().isEmpty() && !node.getIndices().isEmpty()
                    && node.getStage().getType() == null) {
                node.getStage().setType("LEAF");
            }
        }

        log.info("Stage tree assembled: {} roots, {} nodes total", roots.size(), nodeMap.size());
        return roots;
    }

    /** 单根树快捷方法 */
    public StageNode assembleSingle(List<EvalModelStage> stages, List<EvalModelIndex> modelIndices) {
        var roots = assemble(stages, modelIndices);
        if (roots.isEmpty()) return null;
        if (roots.size() > 1) {
            log.warn("Multiple roots ({}), returning first", roots.size());
        }
        return roots.get(0);
    }
}
