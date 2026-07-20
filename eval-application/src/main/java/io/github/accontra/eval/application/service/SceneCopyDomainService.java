package io.github.accontra.eval.application.service;

import io.github.accontra.eval.domain.model.*;
import io.github.accontra.eval.domain.service.*;
import io.github.accontra.eval.infrastructure.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 方案深拷贝 DomainService — 从模型模板创建方案副本。
 *
 * 流程:
 *   1. 查 model → 创建 scene (DRAFT)
 *   2. 拷贝 model_stage → scene_stage (parentId 重映射)
 *   3. 拷贝 model_index → scene_index (stageId 重映射, 填充 indexCode/indexName)
 *
 * Mapper 直调用 (符合 DomainService → Mapper ✅ 规范)
 */
@Component
public class SceneCopyDomainService {

    private static final Logger log = LoggerFactory.getLogger(SceneCopyDomainService.class);

    private final EvalModelService modelService;
    private final EvalModelStageService stageService;
    private final EvalModelIndexService modelIndexService;
    private final EvalIndexService indexService;
    private final EvalSceneMapper sceneMapper;
    private final EvalSceneStageMapper sceneStageMapper;
    private final EvalSceneIndexMapper sceneIndexMapper;
    private final ModelConfigCache configCache;

    public SceneCopyDomainService(EvalModelService modelService,
                                   EvalModelStageService stageService,
                                   EvalModelIndexService modelIndexService,
                                   EvalIndexService indexService,
                                   EvalSceneMapper sceneMapper,
                                   EvalSceneStageMapper sceneStageMapper,
                                   EvalSceneIndexMapper sceneIndexMapper,
                                   ModelConfigCache configCache) {
        this.modelService = modelService;
        this.stageService = stageService;
        this.modelIndexService = modelIndexService;
        this.indexService = indexService;
        this.sceneMapper = sceneMapper;
        this.sceneStageMapper = sceneStageMapper;
        this.sceneIndexMapper = sceneIndexMapper;
        this.configCache = configCache;
    }

    /**
     * 从模型创建方案 — 深拷贝。
     */
    public EvalScene copyFromModel(Long modelId, String sceneCode, String sceneName) {
        var model = modelService.findById(modelId);
        if (model == null) throw new IllegalArgumentException("模型不存在: " + modelId);

        // 1. 创建 Scene (DRAFT)
        EvalScene scene = new EvalScene();
        scene.setCode(sceneCode);
        scene.setName(sceneName != null ? sceneName : model.getName() + "-方案");
        scene.setModelId(modelId);
        scene.setStatus("DRAFT");
        scene.setEnabled(1);
        scene.setCreateTime(LocalDateTime.now());
        scene.setUpdateTime(LocalDateTime.now());
        sceneMapper.insert(scene);
        log.info("[SceneCopy] Scene 创建: id={}, code={}", scene.getId(), scene.getCode());

        // 2. 批量查指标定义
        var indices = modelIndexService.findByModelId(modelId);
        Map<Long, EvalIndex> indexMap = Map.of();
        if (indices != null && !indices.isEmpty()) {
            var indexIds = indices.stream().map(EvalModelIndex::getIndexId).distinct().toList();
            indexMap = indexService.findMapByIds(indexIds);
        }

        // 3. 拷贝 Stage 树 (parentId 重映射)
        var stages = stageService.findByModelId(modelId);
        Map<Long, Long> stageIdMap = new LinkedHashMap<>();
        if (stages != null) {
            for (var stage : stages) {
                var ns = newEvalSceneStage(scene.getId(), stage);
                sceneStageMapper.insert(ns);
                stageIdMap.put(stage.getId(), ns.getId());
            }
            // parentId 重映射
            for (var stage : stages) {
                if (stage.getParentId() != null && stage.getParentId() > 0) {
                    Long newId = stageIdMap.get(stage.getId());
                    Long newParentId = stageIdMap.get(stage.getParentId());
                    if (newId != null && newParentId != null) {
                        var ns = sceneStageMapper.selectById(newId);
                        if (ns != null) {
                            ns.setParentId(newParentId);
                            sceneStageMapper.updateById(ns);
                        }
                    }
                }
            }
            log.info("[SceneCopy] Stage 拷贝: {} 条", stageIdMap.size());
        }

        // 4. 拷贝指标关联
        if (indices != null) {
            int count = 0;
            for (var mi : indices) {
                var ib = indexMap.get(mi.getIndexId());
                if (ib == null) continue;

                var ni = new EvalSceneIndex();
                ni.setSceneId(scene.getId());
                ni.setSourceId(mi.getId());
                ni.setStageId(stageIdMap.get(mi.getStageId()));
                ni.setIndexCode(ib.getCode());
                ni.setIndexName(ib.getName());
                ni.setClazz("INDEX");
                ni.setWeight(1);
                ni.setPriority(mi.getSn());
                ni.setEnabled(1);
                ni.setMemo("copy from model_index:" + mi.getId());
                ni.setCreateTime(LocalDateTime.now());
                ni.setUpdateTime(LocalDateTime.now());
                sceneIndexMapper.insert(ni);
                count++;
            }
            log.info("[SceneCopy] Index 拷贝: {} 条", count);
        }

        log.info("[SceneCopy] 完成: model={} → scene={} (DRAFT)", model.getCode(), scene.getCode());
        return scene;
    }

    /** 发布方案 — DRAFT → PUBLISHED */
    public void publish(Long sceneId) {
        var scene = sceneMapper.selectById(sceneId);
        if (scene == null) throw new IllegalArgumentException("方案不存在: " + sceneId);
        if (!"DRAFT".equals(scene.getStatus()))
            throw new IllegalStateException("只能发布 DRAFT 状态, 当前: " + scene.getStatus());
        scene.setStatus("PUBLISHED");
        scene.setUpdateTime(LocalDateTime.now());
        sceneMapper.updateById(scene);
        configCache.evictScene(scene.getCode());
        log.info("[SceneCopy] 发布: scene={}, cache evicted", scene.getCode());
    }

    private EvalSceneStage newEvalSceneStage(Long sceneId, EvalModelStage stage) {
        var ns = new EvalSceneStage();
        ns.setSceneId(sceneId);
        ns.setSourceId(stage.getId());
        ns.setCode(stage.getCode());
        ns.setName(stage.getName());
        ns.setType(stage.getType());
        ns.setLevel(stage.getLevel());
        ns.setSn(stage.getSn());
        ns.setWeight(stage.getWeight());
        ns.setPriority(stage.getPriority());
        ns.setAggregateMode(stage.getAggregateMode());
        ns.setDefaultScore(stage.getDefaultScore());
        ns.setParentId(stage.getParentId());
        ns.setEnabled(1);
        ns.setMemo("copy from model_stage:" + stage.getId());
        ns.setCreateTime(LocalDateTime.now());
        ns.setUpdateTime(LocalDateTime.now());
        return ns;
    }
}
