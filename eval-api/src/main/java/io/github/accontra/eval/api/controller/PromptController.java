package io.github.accontra.eval.api.controller;

import io.github.accontra.eval.application.service.PromptTemplateService;
import io.github.accontra.eval.common.Result;
import io.github.accontra.eval.domain.model.EvalPromptTemplate;
import io.github.accontra.eval.domain.model.EvalAiExperiment;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import io.github.accontra.eval.infrastructure.mapper.EvalAiExperimentMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalPromptTemplateMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

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
    private final LlmClient llmClient;

    public PromptController(PromptTemplateService promptService,
                             EvalPromptTemplateMapper mapper,
                             EvalAiExperimentMapper experimentMapper,
                             LlmClient llmClient) {
        this.promptService = promptService;
        this.mapper = mapper;
        this.experimentMapper = experimentMapper;
        this.llmClient = llmClient;
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

    /** 多版本对比: 同一组数据用 v1/v2/v3 分别打分 */
    @PostMapping("/compare")
    public Result<Map<String, Object>> compare(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) body.get("data");
        String indicator = (String) body.getOrDefault("indicator", "COST_DEV");
        String indicatorName = (String) body.getOrDefault("indicatorName", "指标");
        Object rawValue = data != null ? data.get(indicator) : body.getOrDefault("value", 0);
        String valStr = rawValue != null ? rawValue.toString() : "0";

        var versions = promptService.listAll();
        Map<String, Object> results = new LinkedHashMap<>();

        for (var tpl : versions) {
            String systemPrompt = tpl.getSystemText();
            String userPrompt = tpl.getUserText()
                    .replace("{{fewShot}}", "")
                    .replace("{{bizId}}", "COMPARE-TEST")
                    .replace("{{indicatorTable}}",
                            String.format("| 指标编码 | 指标名称 | 实际值 | 评分标准 |\n|---------|---------|-------|----|\n| %s | %s | %s | 见评分规则 |",
                                    indicator, indicatorName, valStr))
                    .replace("{{count}}", "1");

            try {
                var resp = llmClient.chatForJson(systemPrompt, userPrompt);
                var json = resp.json();
                if (json != null) {
                    var scores = json.getJSONArray("scores");
                    if (scores != null && !scores.isEmpty()) {
                        var s = scores.getJSONObject(0);
                        results.put(tpl.getVersion(), Map.of(
                                "score", s.getDouble("score", 0.0),
                                "reason", s.getStr("reason", "").substring(0, Math.min(80, s.getStr("reason", "").length())),
                                "tokens", resp.metrics().totalTokens(),
                                "durationMs", resp.metrics().durationMs()
                        ));
                        continue;
                    }
                }
                results.put(tpl.getVersion(), Map.of("error", "JSON parse failed"));
            } catch (Exception e) {
                results.put(tpl.getVersion(), Map.of("error", e.getMessage()));
            }
        }

        return Result.ok(Map.of("indicator", indicator, "value", valStr, "versions", results));
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
