package io.github.accontra.eval.application.strategy;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.domain.model.EvalIndex;
import io.github.accontra.eval.domain.model.EvalIndicatorLog;
import io.github.accontra.eval.domain.model.EvalModelStandard;
import io.github.accontra.eval.application.service.PromptTemplateService;
import io.github.accontra.eval.application.service.SimilarCaseService;
import io.github.accontra.eval.domain.model.EvalAiExperiment;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import io.github.accontra.eval.infrastructure.mapper.EvalAiExperimentMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalIndicatorLogMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalModelStandardMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * LLM-as-Judge 评分策略 — 使用大语言模型对评估指标打分。
 *
 * v2 增强: 从 eval_model_standard 读取评分区间注入 Prompt，
 * 让 LLM 基于真实配置标准打分，而非凭空揣测。
 */
public class LlmScoringStrategy {

    private static final Logger log = LoggerFactory.getLogger(LlmScoringStrategy.class);

    private static final String SYSTEM_PROMPT = """
            你是一个企业级业务评估分析师。你会收到一个评估对象的多个指标数据，
            以及每个指标的评分标准。请对每个指标独立打分（0-100 分），并给出简短理由。

            评分规则：
              - 0-100 分，分数越高表示该指标表现越好
              - 严格参考提供的评分标准区间来打分
              - 如果实际值跨区间，考虑其更接近哪个区间
              - 不要机械地按区间映射，考虑指标的改善/恶化趋势

            回复 MUST 是严格的 JSON 格式，不要包含其他文字:
            {
              "scores": [
                {"indexCode": "xxx", "indexName": "xxx", "score": 85, "reason": "..."}
              ],
              "overallComment": "一句话总体评价"
            }""";

    private static final String USER_PROMPT_TMPL = """
            {{fewShot}}
            ## 当前评估对象
            - 对象ID: {{bizId}}

            ## 指标数据与评分标准
            {{indicatorTable}}

            请对以上 {{count}} 个指标逐一打分。""";

    private final LlmClient llmClient;
    private final EvalModelStandardMapper standardMapper;
    private final EvalIndicatorLogMapper indicatorLogMapper;
    private final EvalAiExperimentMapper experimentMapper;
    private final PromptTemplateService promptService;
    private final SimilarCaseService similarCaseService;

    /** 完整构造 (Spring 注入, A3: +SimilarCaseService) */
    public LlmScoringStrategy(LlmClient llmClient, EvalModelStandardMapper standardMapper,
                               EvalIndicatorLogMapper indicatorLogMapper,
                               EvalAiExperimentMapper experimentMapper,
                               PromptTemplateService promptService,
                               SimilarCaseService similarCaseService) {
        this.llmClient = llmClient;
        this.standardMapper = standardMapper;
        this.indicatorLogMapper = indicatorLogMapper;
        this.experimentMapper = experimentMapper;
        this.promptService = promptService;
        this.similarCaseService = similarCaseService;
    }

    /** 简化构造 (测试用) */
    public LlmScoringStrategy(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.standardMapper = null;
        this.indicatorLogMapper = null;
        this.experimentMapper = null;
        this.promptService = null;
        this.similarCaseService = null;
    }

    public Map<String, ScoreResult> scoreAll(EvaluationContext ctx) {
        try {
            var indexBaseMap = ctx.getIndexBaseMap();
            var rawValues = ctx.getRawValues();
            if (rawValues == null || rawValues.isEmpty()) {
                log.warn("[LLM] rawValues 为空，降级为默认分");
                return degradedScores(ctx);
            }

            // 加载评分标准
            Map<Long, List<EvalModelStandard>> standardsByIndex = loadStandards(ctx);

            // 构建增强表格: 编码 | 名称 | 实际值 | 评分标准区间
            StringBuilder table = new StringBuilder();
            table.append("| 指标编码 | 指标名称 | 实际值 | 评分标准 (min≤值<max → 得分) |\n");
            table.append("|---------|---------|-------|-------------------------------|\n");

            int count = 0;
            for (var mi : ctx.getModelIndices()) {
                EvalIndex ib = indexBaseMap != null
                        ? indexBaseMap.get(String.valueOf(mi.getIndexId())) : null;
                if (ib == null) continue;

                String code = ib.getCode();
                String name = ib.getName() != null ? ib.getName() : code;
                Object value = rawValues.get(code);
                String valStr = value != null ? value.toString() : "无数据";

                // 格式化该指标的评分标准
                String criteria = formatStandards(standardsByIndex.get(mi.getIndexId()));

                table.append(String.format("| %s | %s | %s | %s |\n",
                        code, name, valStr, criteria));
                count++;
            }

            // A1.2: 从 DB 加载 Prompt (一个版本 = system + user)
            String systemPrompt = loadPrompt(true);
            String userTemplate = loadPrompt(false);
            if (userTemplate.isEmpty()) userTemplate = USER_PROMPT_TMPL;

            // Few-shot: 加载历史 TRIVIAL 案例作为参考
            String fewShot = buildFewShot(ctx);
            String userPrompt = userTemplate
                    .replace("{{fewShot}}", fewShot)
                    .replace("{{bizId}}", ctx.getBizId() != null ? ctx.getBizId() : "unknown")
                    .replace("{{indicatorTable}}", table.toString())
                    .replace("{{count}}", String.valueOf(count));

            log.info("[LLM] 请求打分(via DB prompt), bizId={}, indicators={}", ctx.getBizId(), count);
            var resp = llmClient.chatForJson(systemPrompt, userPrompt);
            var json = resp.json();
            if (json == null) {
                log.warn("[LLM] JSON 解析失败, 降级处理");
                return degradedScores(ctx);
            }

            // A2: 记录实验数据
            recordExperiment(ctx, resp, count);

            Map<String, ScoreResult> results = new LinkedHashMap<>();
            JSONArray scores = json.getJSONArray("scores");
            for (int i = 0; i < scores.size(); i++) {
                JSONObject s = scores.getJSONObject(i);
                results.put(s.getStr("indexCode"), new ScoreResult(
                        s.getStr("indexCode"),
                        s.getStr("indexName", ""),
                        BigDecimal.valueOf(s.getDouble("score")).setScale(2, RoundingMode.HALF_UP),
                        s.getStr("reason", "")));
            }

            log.info("[LLM] 打分完成, scores={}, comment={}",
                    results.size(), json.getStr("overallComment", ""));
            return results;

        } catch (Exception e) {
            log.error("[LLM] 打分失败, 降级处理", e);
            return degradedScores(ctx);
        }
    }

    /** A1.2: 从 DB 加载 Prompt (一个版本 = system + user) */
    private String loadPrompt(boolean isSystem) {
        if (promptService == null) return isSystem ? SYSTEM_PROMPT : USER_PROMPT_TMPL;
        var tpl = promptService.getActive();
        if (tpl != null) return isSystem ? tpl.getSystemText() : tpl.getUserText();
        return isSystem ? SYSTEM_PROMPT : USER_PROMPT_TMPL;
    }

    /** 加载模型级评分标准, 按 indexId 分组 */
    private Map<Long, List<EvalModelStandard>> loadStandards(EvaluationContext ctx) {
        if (standardMapper == null) return Map.of();
        Long modelId = ctx.getModel() != null ? ctx.getModel().getId() : null;
        if (modelId == null) return Map.of();

        var qw = new LambdaQueryWrapper<EvalModelStandard>()
                .eq(EvalModelStandard::getModelId, modelId)
                .eq(EvalModelStandard::getEnabled, 1)
                .orderByAsc(EvalModelStandard::getPriority);
        var list = standardMapper.selectList(qw);
        if (list == null || list.isEmpty()) return Map.of();

        Map<Long, List<EvalModelStandard>> grouped = new LinkedHashMap<>();
        for (var s : list) {
            if (s.getIndexId() != null) {
                grouped.computeIfAbsent(s.getIndexId(), k -> new ArrayList<>()).add(s);
            }
        }
        return grouped;
    }

    /** 格式化评分标准为可读字符串 */
    private String formatStandards(List<EvalModelStandard> standards) {
        if (standards == null || standards.isEmpty()) return "未配置标准";

        StringBuilder sb = new StringBuilder();
        for (var s : standards) {
            if (sb.length() > 0) sb.append("; ");
            if (s.getMinValue() != null && s.getMaxValue() != null) {
                sb.append(String.format("%s≤值<%s→%s分",
                        stripZero(s.getMinValue()), stripZero(s.getMaxValue()),
                        s.getScore() != null ? stripZero(s.getScore()) : "?"));
            } else if (s.getMinValue() != null) {
                sb.append(String.format("值≥%s→%s分",
                        stripZero(s.getMinValue()),
                        s.getScore() != null ? stripZero(s.getScore()) : "?"));
            } else if (s.getDimensionRule() != null) {
                sb.append(String.format("条件:%s", s.getDimensionRule()));
            }
        }
        return sb.toString();
    }

    private String stripZero(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** A2: 记录每次 LLM 调用到实验表 */
    private void recordExperiment(EvaluationContext ctx,
                                   LlmClient.LlmJsonResponse resp, int indicatorCount) {
        if (experimentMapper == null) return;
        try {
            var m = resp.metrics();
            for (int i = 0; i < indicatorCount; i++) {
                var exp = new EvalAiExperiment();
                exp.setExperimentType("SCORING");
                exp.setModel(llmClient.getModel());
                // A1.2: 动态读取活跃 Prompt 版本
                var activeTpl = promptService != null ? promptService.getActive() : null;
                exp.setPromptVersion(activeTpl != null ? activeTpl.getVersion() : "unknown");
                exp.setSceneCode(ctx.getSceneCode());
                exp.setBizId(ctx.getBizId());
                exp.setInputTokens(m.inputTokens());
                exp.setOutputTokens(m.outputTokens());
                exp.setDurationMs(m.durationMs());
                exp.setTemperature(BigDecimal.valueOf(llmClient.getTemperature()));
                exp.setErrorType(m.errorType());
                exp.setRetryCount(0);
                // A2.3: 成本计算 (DeepSeek: ¥1/M input, ¥2/M output)
                double cost = (m.inputTokens() * 0.001 + m.outputTokens() * 0.002) / 1000.0;
                exp.setCost(BigDecimal.valueOf(Math.round(cost * 1000000.0) / 1000000.0));
                exp.setCreatedAt(java.time.LocalDateTime.now());
                experimentMapper.insert(exp);
            }
        } catch (Exception e) {
            log.debug("[Experiment] 记录失败: {}", e.getMessage());
        }
    }

    /** A3: 用 RAG 检索相似案例替代简单 TRIVIAL 查询 */
    private String buildFewShot(EvaluationContext ctx) {
        if (similarCaseService == null || ctx.getRawValues() == null) return "";
        try {
            // 取第一个指标的值作为查询条件
            var rawValues = ctx.getRawValues();
            if (rawValues.isEmpty()) return "";
            var firstEntry = rawValues.entrySet().iterator().next();
            String indexCode = firstEntry.getKey();
            Object rawValue = firstEntry.getValue();

            var cases = similarCaseService.findSimilar(indexCode, rawValue, 3);
            if (cases.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("## 历史相似案例（基于指标特征检索）\n\n");
            int n = 0;
            for (var c : cases) {
                n++;
                sb.append(String.format("案例%d: %s (相似度:%.0f%%)\n",
                        n, c.toPromptExample(), c.similarity()));
            }
            sb.append("\n");
            return sb.toString();
        } catch (Exception e) {
            log.warn("[RAG] 检索失败: {}", e.getMessage());
            return "";
        }
    }

    /** (废弃) 从最近 TRIVIAL 对比记录中提取 few-shot 示例 */
    @Deprecated
    private String buildFewShotLegacy(EvaluationContext ctx) {
        if (indicatorLogMapper == null) return "";
        try {
            var qw = new LambdaQueryWrapper<EvalIndicatorLog>()
                    .eq(EvalIndicatorLog::getDiffLevel, "TRIVIAL")
                    .isNotNull(EvalIndicatorLog::getLlmReason)
                    .orderByDesc(EvalIndicatorLog::getId)
                    .last("LIMIT 3");
            var examples = indicatorLogMapper.selectList(qw);
            if (examples == null || examples.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("## 历史参考案例（AI打分与规则引擎一致的高质量案例）\n\n");
            int n = 0;
            for (var ex : examples) {
                n++;
                sb.append(String.format("案例%d: %s=%.1f分, %s\n",
                        n,
                        ex.getIndexCode() != null ? ex.getIndexCode() : "?",
                        ex.getLlmScore() != null ? ex.getLlmScore() : 0,
                        ex.getLlmReason() != null ? ex.getLlmReason() : ""));
            }
            sb.append("\n");
            return sb.toString();
        } catch (Exception e) {
            log.warn("[LLM] Few-shot 加载失败: {}", e.getMessage());
            return "";
        }
    }

    private Map<String, ScoreResult> degradedScores(EvaluationContext ctx) {
        Map<String, ScoreResult> results = new LinkedHashMap<>();
        var indexBaseMap = ctx.getIndexBaseMap();
        if (ctx.getModelIndices() == null) return results;
        for (var mi : ctx.getModelIndices()) {
            EvalIndex ib = indexBaseMap != null
                    ? indexBaseMap.get(String.valueOf(mi.getIndexId())) : null;
            if (ib == null) continue;
            results.put(ib.getCode(), new ScoreResult(
                    ib.getCode(), ib.getName(), BigDecimal.valueOf(70), "LLM 不可用，默认 70 分"));
        }
        return results;
    }

    public record ScoreResult(String indexCode, String indexName,
                              BigDecimal score, String reason) {}
}
