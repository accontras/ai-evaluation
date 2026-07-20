package io.github.accontra.eval.application.strategy;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.domain.model.EvalIndex;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import io.github.accontra.eval.infrastructure.llm.PromptTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM-as-Judge 评分策略 — 使用大语言模型对评估指标打分。
 *
 * 核心 Prompt 设计:
 *   1. 系统提示词设定角色和输出格式
 *   2. 用户提示词包含指标数据 + 参考标准 + 上下文
 *   3. LLM 返回 JSON: { scores: [{ indexCode, score, reason }] }
 *   4. 失败时降级为默认 70 分 (DEGRADED)
 */
public class LlmScoringStrategy {

    private static final Logger log = LoggerFactory.getLogger(LlmScoringStrategy.class);

    private static final String SYSTEM_PROMPT = """
            你是一个企业级业务评估分析师。你会收到一个评估对象的多个指标数据。
            请对每个指标独立打分（0-100 分），并给出简短理由。

            评分规则：
              - 0-100 分，分数越高表示该指标表现越好
              - 将指标实际值与参考标准对比，考虑趋势和上下文
              - 不要机械地按区间映射，考虑指标的改善/恶化趋势

            回复 MUST 是严格的 JSON 格式，不要包含其他文字:
            {
              "scores": [
                {"indexCode": "xxx", "indexName": "xxx", "score": 85, "reason": "..."}
              ],
              "overallComment": "一句话总体评价"
            }""";

    private static final String USER_PROMPT_TMPL = """
            ## 评估对象
            - 对象ID: {{bizId}}

            ## 指标数据
            {{indicatorTable}}

            请对以上 {{count}} 个指标逐一打分。""";

    private final LlmClient llmClient;

    public LlmScoringStrategy(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 对 Context 中的全部指标进行 LLM 打分。
     *
     * @return Map<indexCode, ScoreResult>
     */
    public Map<String, ScoreResult> scoreAll(EvaluationContext ctx) {
        try {
            // 构建指标表格
            var indexBaseMap = ctx.getIndexBaseMap();
            var rawValues = ctx.getRawValues();
            if (rawValues == null || rawValues.isEmpty()) {
                log.warn("[LLM] rawValues 为空，降级为默认分");
                return degradedScores(ctx);
            }

            StringBuilder table = new StringBuilder();
            table.append("| 指标编码 | 指标名称 | 实际值 |\n");
            table.append("|---------|---------|-------|\n");

            int count = 0;
            for (var mi : ctx.getModelIndices()) {
                EvalIndex ib = indexBaseMap != null
                        ? indexBaseMap.get(String.valueOf(mi.getIndexId())) : null;
                if (ib == null) continue;

                String code = ib.getCode();
                String name = ib.getName() != null ? ib.getName() : code;
                Object value = rawValues.get(code);
                table.append(String.format("| %s | %s | %s |\n", code, name,
                        value != null ? value.toString() : "无数据"));
                count++;
            }

            // 构建 Prompt
            var tpl = PromptTemplate.of(USER_PROMPT_TMPL);
            var userPrompt = tpl.render(Map.of(
                    "bizId", ctx.getBizId() != null ? ctx.getBizId() : "unknown",
                    "indicatorTable", table.toString(),
                    "count", String.valueOf(count)
            ));

            log.info("[LLM] 请求打分, bizId={}, indicators={}", ctx.getBizId(), count);
            var json = llmClient.chatForJson(SYSTEM_PROMPT, userPrompt);

            // 解析结果
            Map<String, ScoreResult> results = new LinkedHashMap<>();
            JSONArray scores = json.getJSONArray("scores");
            for (int i = 0; i < scores.size(); i++) {
                JSONObject s = scores.getJSONObject(i);
                ScoreResult r = new ScoreResult(
                        s.getStr("indexCode"),
                        s.getStr("indexName", ""),
                        BigDecimal.valueOf(s.getDouble("score")).setScale(2, RoundingMode.HALF_UP),
                        s.getStr("reason", "")
                );
                results.put(r.indexCode, r);
            }

            String comment = json.getStr("overallComment", "");
            log.info("[LLM] 打分完成, scores={}, comment={}", results.size(), comment);

            return results;

        } catch (Exception e) {
            log.error("[LLM] 打分失败, 降级处理", e);
            return degradedScores(ctx);
        }
    }

    /** 降级: LLM 不可用时返回默认 70 分 */
    private Map<String, ScoreResult> degradedScores(EvaluationContext ctx) {
        Map<String, ScoreResult> results = new LinkedHashMap<>();
        var indexBaseMap = ctx.getIndexBaseMap();
        if (ctx.getModelIndices() == null) return results;

        for (var mi : ctx.getModelIndices()) {
            EvalIndex ib = indexBaseMap != null
                    ? indexBaseMap.get(String.valueOf(mi.getIndexId())) : null;
            if (ib == null) continue;

            results.put(ib.getCode(), new ScoreResult(
                    ib.getCode(), ib.getName(),
                    BigDecimal.valueOf(70), "LLM 不可用，默认 70 分"));
        }
        return results;
    }

    /** LLM 评分结果 */
    public record ScoreResult(String indexCode, String indexName,
                              BigDecimal score, String reason) {}
}
