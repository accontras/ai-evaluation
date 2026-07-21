package io.github.accontra.eval.api.controller;

import io.github.accontra.eval.application.service.PromptTemplateService;
import io.github.accontra.eval.common.Result;
import io.github.accontra.eval.domain.model.EvalPromptTemplate;
import io.github.accontra.eval.domain.model.EvalAiExperiment;
import io.github.accontra.eval.infrastructure.mapper.EvalAiExperimentMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalPromptTemplateMapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Prompt 版本管理 — A1.2。
 */
@RestController
@RequestMapping("/api/v1/prompts")
public class PromptController {

    private final PromptTemplateService promptService;
    private final EvalPromptTemplateMapper mapper;
    private final EvalAiExperimentMapper experimentMapper;

    public PromptController(PromptTemplateService promptService,
                             EvalPromptTemplateMapper mapper,
                             EvalAiExperimentMapper experimentMapper) {
        this.promptService = promptService;
        this.mapper = mapper;
        this.experimentMapper = experimentMapper;
    }

    /** 列出所有 Prompt 模板 */
    @GetMapping
    public Result<List<EvalPromptTemplate>> listAll() {
        return Result.ok(promptService.listAll());
    }

    /** 激活指定版本 */
    @PostMapping("/{id}/activate")
    public Result<Map<String, Object>> activate(@PathVariable("id") Long id) {
        promptService.activate(id);
        var tpl = mapper.selectById(id);
        return Result.ok(Map.of("id", id, "promptKey", tpl.getPromptKey(),
                "version", tpl.getVersion(), "status", "ACTIVE"));
    }

    /** 按版本聚合统计 (基于 eval_ai_experiment) */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        var experiments = experimentMapper.selectList(null);
        if (experiments == null || experiments.isEmpty())
            return Result.ok(Map.of("message", "暂无实验数据"));

        Map<String, List<EvalAiExperiment>> byVersion = new LinkedHashMap<>();
        for (var e : experiments) {
            String v = e.getPromptVersion() != null ? e.getPromptVersion() : "unknown";
            byVersion.computeIfAbsent(v, k -> new ArrayList<>()).add(e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : byVersion.entrySet()) {
            var list = entry.getValue();
            long calls = list.size();
            double avgDur = list.stream().filter(e -> e.getDurationMs() != null)
                    .mapToLong(EvalAiExperiment::getDurationMs).average().orElse(0);
            long totalTokens = list.stream().filter(e -> e.getInputTokens() != null)
                    .mapToLong(e -> e.getInputTokens() + (e.getOutputTokens() != null ? e.getOutputTokens() : 0)).sum();
            long errors = list.stream().filter(e -> e.getErrorType() != null).count();
            result.put(entry.getKey(), Map.of(
                    "calls", calls, "avgDurationMs", Math.round(avgDur),
                    "totalTokens", totalTokens,
                    "errorRate", calls > 0 ? String.format("%.1f%%", 100.0 * errors / calls) : "0%"));
        }
        return Result.ok(result);
    }
}
