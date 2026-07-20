package io.github.accontra.eval.application.service;

import cn.hutool.json.JSONObject;
import io.github.accontra.eval.domain.model.EvalObjectLog;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import io.github.accontra.eval.infrastructure.mapper.EvalObjectLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 总结服务 — 两轮对话生成评估总结。
 *
 * Round 1: LLM 生成评估总结（指标得分 + 对比数据 + 事件）
 * Round 2: LLM 自审（检查遗漏、措辞、数据引用准确性）
 *
 * 降级: LLM 不可用 → 模板化总结
 */
@Component
public class AiSummaryService {

    private static final Logger log = LoggerFactory.getLogger(AiSummaryService.class);

    private static final String ROUND1_SYSTEM = """
            你是一个企业级业务评估分析师。请根据以下评估结果生成一份简洁的评估总结。

            要求:
            1. 一两句话概括整体表现
            2. 指出最突出的优势（得分最高的指标）
            3. 指出最需要关注的问题（得分最低的指标 + 事件/红线）
            4. 给出改进建议

            回复 MUST 是严格的 JSON:
            {
              "overall": "整体评价一句话",
              "strength": "最大优势",
              "weakness": "最需关注的问题",
              "suggestion": "改进建议"
            }""";

    private static final String ROUND2_SYSTEM = """
            你是一个严格的审阅者。以下是一份评估总结草稿。请检查:

            1. 有没有遗漏的重要异常信号？
            2. 措辞是否过分严厉或乐观？
            3. 数据引用是否准确？

            如果发现问题，请直接修改。返回修改后的 JSON (格式相同):
            {
              "overall": "...",
              "strength": "...",
              "weakness": "...",
              "suggestion": "..."
            }""";

    private final LlmClient llmClient;
    private final EvalObjectLogMapper objectLogMapper;

    public AiSummaryService(LlmClient llmClient, EvalObjectLogMapper objectLogMapper) {
        this.llmClient = llmClient;
        this.objectLogMapper = objectLogMapper;
    }

    /**
     * 为指定对象生成 AI 总结（同步，调用方负责异步化）。
     */
    public String generateSummary(Long objectLogId) {
        var obj = objectLogMapper.selectById(objectLogId);
        if (obj == null) {
            log.warn("[AI-Summary] Object not found: {}", objectLogId);
            return null;
        }

        try {
            // 构建上下文
            String context = buildContext(obj);

            // Round 1: 生成
            String userPrompt1 = "## 评估结果数据\n\n" + context + "\n\n请生成评估总结。";
            log.info("[AI-Summary] Round1: objectLogId={}", objectLogId);
            JSONObject r1 = llmClient.chatForJson(ROUND1_SYSTEM, userPrompt1);

            // Round 2: 自审
            String draft = r1.toString();
            String userPrompt2 = "## 评估总结草稿\n\n" + draft + "\n\n## 原始数据\n\n" + context + "\n\n请审阅并修改。";
            log.info("[AI-Summary] Round2: objectLogId={}", objectLogId);
            JSONObject r2 = llmClient.chatForJson(ROUND2_SYSTEM, userPrompt2);

            // 组装最终总结
            String summary = String.format(
                    "【整体】%s\n【优势】%s\n【问题】%s\n【建议】%s",
                    r2.getStr("overall", r1.getStr("overall", "N/A")),
                    r2.getStr("strength", r1.getStr("strength", "N/A")),
                    r2.getStr("weakness", r1.getStr("weakness", "N/A")),
                    r2.getStr("suggestion", r1.getStr("suggestion", "N/A"))
            );

            // 落库
            obj.setSummary(summary);
            obj.setSummaryStatus("DONE");
            obj.setUpdateTime(LocalDateTime.now());
            objectLogMapper.updateById(obj);

            log.info("[AI-Summary] 完成: objectLogId={}, length={}", objectLogId, summary.length());
            return summary;

        } catch (Exception e) {
            log.error("[AI-Summary] 失败, 降级为模板: objectLogId={}", objectLogId, e);
            return fallbackSummary(obj);
        }
    }

    private String buildContext(EvalObjectLog obj) {
        var sb = new StringBuilder();
        sb.append("- 对象ID: ").append(obj.getTargetCode() != null ? obj.getTargetCode() : "N/A").append("\n");
        sb.append("- 场景: ").append(obj.getSceneCode() != null ? obj.getSceneCode() : "N/A").append("\n");
        sb.append("- 总分: ").append(obj.getTotalScore() != null ? obj.getTotalScore() : 0).append("\n");
        sb.append("- 风险等级: ").append(obj.getRiskLevel() != null ? obj.getRiskLevel() : "N/A").append("\n");
        sb.append("- 等级: ").append(obj.getGrade() != null ? obj.getGrade() : "N/A").append("\n");
        sb.append("- 排名: ").append(obj.getEvalRank() != null ? obj.getEvalRank() : "N/A")
                .append("/").append(obj.getRankTotal() != null ? obj.getRankTotal() : "N/A").append("\n");
        return sb.toString();
    }

    private String fallbackSummary(EvalObjectLog obj) {
        BigDecimal score = obj.getTotalScore() != null ? obj.getTotalScore() : BigDecimal.ZERO;
        String level = score.compareTo(BigDecimal.valueOf(80)) >= 0 ? "良好"
                : score.compareTo(BigDecimal.valueOf(60)) >= 0 ? "一般" : "需关注";
        String summary = String.format(
                "【整体】该评估对象综合得分%.1f分，表现%s。\n【优势】—\n【问题】—\n【建议】持续关注指标变化趋势。",
                score, level);
        obj.setSummary(summary);
        obj.setSummaryStatus("FALLBACK");
        obj.setUpdateTime(LocalDateTime.now());
        objectLogMapper.updateById(obj);
        return summary;
    }
}
