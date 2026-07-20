package io.github.accontra.eval.application.handler;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.common.enums.ErrorCode;
import io.github.accontra.eval.common.exception.EvalException;
import io.github.accontra.eval.domain.model.EvalIndex;
import io.github.accontra.eval.domain.model.EvalModelIndex;
import io.github.accontra.eval.domain.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * H1 — 加载评估配置。
 * 从 DB 加载 scene → model → stages → indices → indexBase，
 * 写入 EvaluationContext 供后续 Handler 使用。
 *
 * S9 最简版: 单层 stage，不装配树，不加载事件/标准/维度。
 */
public class ValidateAndLoadModelHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(ValidateAndLoadModelHandler.class);

    private final EvalSceneService sceneService;
    private final EvalModelService modelService;
    private final EvalModelStageService stageService;
    private final EvalModelIndexService modelIndexService;
    private final EvalIndexService indexService;

    public ValidateAndLoadModelHandler(EvalSceneService sceneService, EvalModelService modelService,
            EvalModelStageService stageService, EvalModelIndexService modelIndexService,
            EvalIndexService indexService) {
        this.sceneService = sceneService;
        this.modelService = modelService;
        this.stageService = stageService;
        this.modelIndexService = modelIndexService;
        this.indexService = indexService;
    }

    @Override public String stepCode() { return "VALIDATE"; }
    @Override public String stepName() { return "加载配置"; }
    @Override public int order() { return 1; }

    @Override
    public void execute(EvaluationContext ctx) {
        // 1. 加载方案
        var scene = sceneService.findByCode(ctx.getSceneCode());
        if (scene == null) throw new EvalException(ErrorCode.SCENE_NOT_FOUND.code(),
                ErrorCode.SCENE_NOT_FOUND.message());
        ctx.setScene(scene);

        // 2. 加载模型
        var model = modelService.findById(scene.getModelId());
        if (model == null) throw new EvalException(ErrorCode.MODEL_NOT_FOUND.code(),
                ErrorCode.MODEL_NOT_FOUND.message());
        ctx.setModel(model);

        // 3. 加载 stage (S9 最简: 不去装配树)
        var stages = stageService.findByModelId(model.getId());
        ctx.setStages(stages);

        // 4. 加载指标关联
        List<EvalModelIndex> indices = modelIndexService.findByModelId(model.getId());
        if (indices.isEmpty()) throw new EvalException(ErrorCode.MODEL_NO_INDICES.code(),
                ErrorCode.MODEL_NO_INDICES.message());
        ctx.setModelIndices(indices);

        // 5. 加载指标定义
        List<Long> indexIds = indices.stream().map(EvalModelIndex::getIndexId).toList();
        Map<Long, EvalIndex> indexMap = indexService.findMapByIds(indexIds);
        // 转为 Map<String, EvalIndex> key=string(id)
        Map<String, EvalIndex> stringMap = indexMap.entrySet().stream()
                .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue));
        ctx.setIndexBaseMap(stringMap);

        log.info("[H1] 配置加载完成: scene={}, model={}, stages={}, indices={}",
                scene.getCode(), model.getCode(), stages.size(), indices.size());
    }
}
