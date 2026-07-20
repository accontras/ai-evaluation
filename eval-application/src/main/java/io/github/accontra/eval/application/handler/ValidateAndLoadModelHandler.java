package io.github.accontra.eval.application.handler;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.service.ModelConfigCache;
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
 *
 * S25: 集成 ModelConfigCache (Caffeine TTL 5min)
 * 优先走缓存，miss 时回源 DB。
 */
public class ValidateAndLoadModelHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(ValidateAndLoadModelHandler.class);

    private final EvalSceneService sceneService;
    private final EvalModelService modelService;
    private final EvalModelStageService stageService;
    private final EvalModelIndexService modelIndexService;
    private final EvalIndexService indexService;
    private final ModelConfigCache configCache;

    // S25: with cache
    public ValidateAndLoadModelHandler(ModelConfigCache configCache,
            EvalIndexService indexService) {
        this.sceneService = null;
        this.modelService = null;
        this.stageService = null;
        this.modelIndexService = null;
        this.indexService = indexService;
        this.configCache = configCache;
    }

    // Legacy: direct DB (for tests that don't have cache)
    public ValidateAndLoadModelHandler(EvalSceneService sceneService, EvalModelService modelService,
            EvalModelStageService stageService, EvalModelIndexService modelIndexService,
            EvalIndexService indexService) {
        this.sceneService = sceneService;
        this.modelService = modelService;
        this.stageService = stageService;
        this.modelIndexService = modelIndexService;
        this.indexService = indexService;
        this.configCache = null;
    }

    @Override public String stepCode() { return "VALIDATE"; }
    @Override public String stepName() { return "加载配置"; }
    @Override public int order() { return 1; }

    @Override
    public void execute(EvaluationContext ctx) {
        if (configCache != null) {
            executeWithCache(ctx);
        } else {
            executeDirect(ctx);
        }
    }

    /** S25: 缓存路径 */
    private void executeWithCache(EvaluationContext ctx) {
        var snap = configCache.loadFullConfig(ctx.getSceneCode());
        if (snap == null || snap.scene() == null)
            throw new EvalException(ErrorCode.SCENE_NOT_FOUND.code(), ErrorCode.SCENE_NOT_FOUND.message());
        if (snap.model() == null)
            throw new EvalException(ErrorCode.MODEL_NOT_FOUND.code(), ErrorCode.MODEL_NOT_FOUND.message());
        if (snap.indices().isEmpty())
            throw new EvalException(ErrorCode.MODEL_NO_INDICES.code(), ErrorCode.MODEL_NO_INDICES.message());

        ctx.setScene(snap.scene());
        ctx.setModel(snap.model());
        ctx.setStages(snap.stages());
        ctx.setModelIndices(snap.indices());

        Map<String, EvalIndex> stringMap = snap.indexMap().entrySet().stream()
                .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue));
        ctx.setIndexBaseMap(stringMap);

        log.info("[H1] cached: scene={}, model={}, stages={}, indices={}",
                snap.scene().getCode(), snap.model().getCode(),
                snap.stages().size(), snap.indices().size());
    }

    /** 直连 DB 路径 (兼容旧测试) */
    private void executeDirect(EvaluationContext ctx) {
        var scene = sceneService.findByCode(ctx.getSceneCode());
        if (scene == null) throw new EvalException(ErrorCode.SCENE_NOT_FOUND.code(), ErrorCode.SCENE_NOT_FOUND.message());
        ctx.setScene(scene);

        var model = modelService.findById(scene.getModelId());
        if (model == null) throw new EvalException(ErrorCode.MODEL_NOT_FOUND.code(), ErrorCode.MODEL_NOT_FOUND.message());
        ctx.setModel(model);

        var stages = stageService.findByModelId(model.getId());
        ctx.setStages(stages);

        List<EvalModelIndex> indices = modelIndexService.findByModelId(model.getId());
        if (indices.isEmpty()) throw new EvalException(ErrorCode.MODEL_NO_INDICES.code(), ErrorCode.MODEL_NO_INDICES.message());
        ctx.setModelIndices(indices);

        List<Long> indexIds = indices.stream().map(EvalModelIndex::getIndexId).toList();
        Map<Long, EvalIndex> indexMap = indexService.findMapByIds(indexIds);
        Map<String, EvalIndex> stringMap = indexMap.entrySet().stream()
                .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue));
        ctx.setIndexBaseMap(stringMap);

        log.info("[H1] direct: scene={}, model={}, stages={}, indices={}",
                scene.getCode(), model.getCode(), stages.size(), indices.size());
    }
}
