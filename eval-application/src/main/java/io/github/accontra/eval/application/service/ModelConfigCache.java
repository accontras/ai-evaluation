package io.github.accontra.eval.application.service;

import io.github.accontra.eval.domain.model.*;
import io.github.accontra.eval.domain.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模型配置缓存 — S25。
 *
 * 使用 Spring Cache + Caffeine 缓存 scene→model→stages→indices 链路。
 * TTL: 5min (见 CacheConfig)
 * 失效: scene 发布/更新时 evict
 */
@Component
public class ModelConfigCache {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigCache.class);

    private final EvalSceneService sceneService;
    private final EvalModelService modelService;
    private final EvalModelStageService stageService;
    private final EvalModelIndexService modelIndexService;
    private final EvalIndexService indexService;

    public ModelConfigCache(EvalSceneService sceneService, EvalModelService modelService,
                             EvalModelStageService stageService, EvalModelIndexService modelIndexService,
                             EvalIndexService indexService) {
        this.sceneService = sceneService;
        this.modelService = modelService;
        this.stageService = stageService;
        this.modelIndexService = modelIndexService;
        this.indexService = indexService;
    }

    /** 缓存: Scene */
    @Cacheable(value = "modelConfig", key = "'scene:' + #sceneCode")
    public EvalScene getScene(String sceneCode) {
        log.debug("[Cache] MISS scene: {}", sceneCode);
        return sceneService.findByCode(sceneCode);
    }

    /** 缓存: Model */
    @Cacheable(value = "modelConfig", key = "'model:' + #modelId")
    public EvalModel getModel(Long modelId) {
        log.debug("[Cache] MISS model: {}", modelId);
        return modelService.findById(modelId);
    }

    /** 缓存: Model Stages */
    @Cacheable(value = "modelConfig", key = "'stages:' + #modelId")
    public List<EvalModelStage> getStages(Long modelId) {
        log.debug("[Cache] MISS stages: {}", modelId);
        return stageService.findByModelId(modelId);
    }

    /** 缓存: Model Indices */
    @Cacheable(value = "modelConfig", key = "'indices:' + #modelId")
    public List<EvalModelIndex> getIndices(Long modelId) {
        log.debug("[Cache] MISS indices: {}", modelId);
        return modelIndexService.findByModelId(modelId);
    }

    /** 加载全链路配置 (内部调用, 复用缓存) */
    public ConfigSnapshot loadFullConfig(String sceneCode) {
        var scene = getScene(sceneCode);
        if (scene == null) return null;

        Long modelId = scene.getModelId();
        var model = getModel(modelId);
        if (model == null) return null;

        var stages = getStages(modelId);
        var indices = getIndices(modelId);

        // 指标定义 (高频但变化少, 暂不缓存)
        var indexIds = indices.stream().map(EvalModelIndex::getIndexId).distinct().toList();
        var indexMap = indexService.findMapByIds(indexIds);

        log.debug("[Cache] Loaded config: scene={}, model={}, stages={}, indices={}",
                sceneCode, model.getCode(), stages.size(), indices.size());

        return new ConfigSnapshot(scene, model, stages, indices, indexMap);
    }

    /** 失效缓存: scene 变更时调用 */
    @CacheEvict(value = "modelConfig", key = "'scene:' + #sceneCode")
    public void evictScene(String sceneCode) {
        log.info("[Cache] Evicted scene: {}", sceneCode);
    }

    /** 失效缓存: model 变更时调用 */
    @CacheEvict(value = "modelConfig", allEntries = true)
    public void evictAll() {
        log.info("[Cache] Evicted all model config");
    }

    /** 配置快照 */
    public record ConfigSnapshot(EvalScene scene, EvalModel model,
                                  List<EvalModelStage> stages, List<EvalModelIndex> indices,
                                  java.util.Map<Long, EvalIndex> indexMap) {}
}
