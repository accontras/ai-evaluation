package io.github.accontra.eval.api.controller;

import io.github.accontra.eval.application.service.SceneCopyDomainService;
import io.github.accontra.eval.common.Result;
import io.github.accontra.eval.infrastructure.mapper.EvalSceneMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 方案配置 Controller — 创建/查询/发布方案。
 */
@RestController
@RequestMapping("/api/v1/scene")
public class SceneController {

    private static final Logger log = LoggerFactory.getLogger(SceneController.class);

    private final SceneCopyDomainService copyService;
    private final EvalSceneMapper sceneMapper;

    public SceneController(SceneCopyDomainService copyService, EvalSceneMapper sceneMapper) {
        this.copyService = copyService;
        this.sceneMapper = sceneMapper;
    }

    /** 从模型创建方案 */
    @PostMapping("/copy")
    public Result<Map<String, Object>> copy(@RequestBody Map<String, Object> body) {
        Long modelId = Long.valueOf(body.get("modelId").toString());
        String sceneCode = (String) body.get("sceneCode");
        String sceneName = (String) body.getOrDefault("sceneName", null);

        var scene = copyService.copyFromModel(modelId, sceneCode, sceneName);

        return Result.ok(Map.of(
                "id", scene.getId(),
                "code", scene.getCode(),
                "name", scene.getName(),
                "modelId", scene.getModelId(),
                "status", scene.getStatus()
        ));
    }

    /** 方案列表 */
    @GetMapping("/list")
    public Result<Object> list() {
        var scenes = sceneMapper.selectList(null);
        return Result.ok(scenes);
    }

    /** 发布方案 */
    @PostMapping("/{id}/publish")
    public Result<Map<String, Object>> publish(@PathVariable("id") Long id) {
        copyService.publish(id);
        return Result.ok(Map.of("id", id, "status", "PUBLISHED"));
    }
}
