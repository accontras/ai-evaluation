package io.github.accontra.eval.application.event;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

/**
 * LLM 异常检测器 — 用大模型判断是否存在数据异常、红线风险。
 *
 * Prompt 设计:
 *   给定指标值 + LLM 打分结果，请判断该对象是否存在异常或需要人工关注的风险。
 *   返回 JSON: { hasAnomaly, riskLevel, description, redLineCandidates[] }
 */
public class LlmEventDetector {

    private static final Logger log = LoggerFactory.getLogger(LlmEventDetector.class);

    private static final String SYSTEM_PROMPT = """
            你是一个企业级业务风险评估专家。你会收到一个评估对象的指标数据和AI打分结果。
            请判断该对象是否存在数据异常、恶意规避或需要人工关注的风险点。

            回复 MUST 是严格的 JSON 格式:
            {
              "hasAnomaly": true/false,
              "riskLevel": "NONE" / "LOW" / "HIGH",
              "description": "一句话描述风险情况",
              "redLineCandidates": ["可能触发的红线类型1", "红线类型2"]
            }""";

    private static final String USER_PROMPT_TMPL = """
            ## 评估对象
            - 对象ID: {{bizId}}
            - 场景: {{sceneCode}}

            ## 指标得分
            {{indicatorTable}}

            ## 总分
            totalScore: {{totalScore}}

            ## 风险等级
            riskLevel: {{riskLevel}}

            请判断该对象是否存在异常信号。""";

    private final LlmClient llmClient;

    public LlmEventDetector(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * LLM 异常检测。
     */
    public AnomalyResult detect(EvaluationContext ctx) {
        try {
            // 构建指标表格
            StringBuilder table = new StringBuilder();
            table.append("| 指标编码 | 指标名称 | LLM得分 |\n");
            table.append("|---------|---------|-------|\n");

            var llmScores = ctx.getLlmScores();
            var indexBaseMap = ctx.getIndexBaseMap();
            if (ctx.getModelIndices() != null && llmScores != null) {
                for (var mi : ctx.getModelIndices()) {
                    var ib = indexBaseMap != null
                            ? indexBaseMap.get(String.valueOf(mi.getIndexId())) : null;
                    if (ib == null) continue;
                    var sr = llmScores.get(ib.getCode());
                    table.append(String.format("| %s | %s | %s |\n",
                            ib.getCode(), ib.getName(),
                            sr != null ? sr.score().toString() : "N/A"));
                }
            }

            Map<String, String> vars = Map.of(
                    "bizId", ctx.getBizId() != null ? ctx.getBizId() : "unknown",
                    "sceneCode", ctx.getSceneCode() != null ? ctx.getSceneCode() : "unknown",
                    "indicatorTable", table.toString(),
                    "totalScore", ctx.getTotalScore() != null ? ctx.getTotalScore().toString() : "0",
                    "riskLevel", ctx.getRiskLevel() != null ? ctx.getRiskLevel() : "N/A"
            );

            // 简单模板渲染
            String userPrompt = USER_PROMPT_TMPL;
            for (var entry : vars.entrySet()) {
                userPrompt = userPrompt.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }

            log.info("[LLM-Event] 开始异常检测, bizId={}", ctx.getBizId());
            JSONObject json = llmClient.chatForJson(SYSTEM_PROMPT, userPrompt);

            boolean hasAnomaly = json.getBool("hasAnomaly", false);
            String riskLevel = json.getStr("riskLevel", "NONE");
            String description = json.getStr("description", "");
            var candidates = json.getJSONArray("redLineCandidates");
            List<String> candidateList = new ArrayList<>();
            if (candidates != null) {
                for (int i = 0; i < candidates.size(); i++) {
                    candidateList.add(candidates.getStr(i));
                }
            }

            log.info("[LLM-Event] 检测完成: hasAnomaly={}, riskLevel={}, candidates={}",
                    hasAnomaly, riskLevel, candidateList);

            return new AnomalyResult(hasAnomaly, riskLevel, description, candidateList);

        } catch (Exception e) {
            log.error("[LLM-Event] 异常检测失败", e);
            return new AnomalyResult(false, "NONE", "LLM 不可用", List.of());
        }
    }

    /** LLM 异常检测结果 */
    public record AnomalyResult(boolean hasAnomaly, String riskLevel,
                                 String description, List<String> redLineCandidates) {}
}
