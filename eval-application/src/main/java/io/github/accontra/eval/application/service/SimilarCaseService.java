package io.github.accontra.eval.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.accontra.eval.domain.model.EvalIndicatorLog;
import io.github.accontra.eval.infrastructure.mapper.EvalIndicatorLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 相似案例检索 — A3 RAG。
 *
 * 基于特征相似度（指标编码 + 得分接近度 + 差异级别）检索历史高质量案例。
 * 替代简单的 "最新 3 条 TRIVIAL" 策略，提供更精准的 few-shot 示例。
 */
@Component
public class SimilarCaseService {

    private static final Logger log = LoggerFactory.getLogger(SimilarCaseService.class);
    private final EvalIndicatorLogMapper indicatorLogMapper;

    public SimilarCaseService(EvalIndicatorLogMapper indicatorLogMapper) {
        this.indicatorLogMapper = indicatorLogMapper;
    }

    /**
     * 检索与指定指标最相似的历史案例。
     *
     * @param indexCode  目标指标编码
     * @param rawValue   目标实际值
     * @param limit      返回数量
     * @return 相似案例列表 (按相似度降序)
     */
    public List<SimilarCase> findSimilar(String indexCode, Object rawValue, int limit) {
        // 查询同指标编码的历史记录 (优先 TRIVIAL/NOTABLE)
        var qw = new LambdaQueryWrapper<EvalIndicatorLog>()
                .eq(EvalIndicatorLog::getIndexCode, indexCode)
                .isNotNull(EvalIndicatorLog::getLlmScore)
                .isNotNull(EvalIndicatorLog::getLlmReason)
                .orderByDesc(EvalIndicatorLog::getId)
                .last("LIMIT 100");
        var candidates = indicatorLogMapper.selectList(qw);
        if (candidates == null || candidates.isEmpty()) return List.of();

        BigDecimal targetValue = toBigDecimal(rawValue);

        // 计算相似度分数
        List<SimilarCase> scored = new ArrayList<>();
        for (var c : candidates) {
            double score = computeSimilarity(c, targetValue);
            scored.add(new SimilarCase(c, score));
        }

        // 按相似度降序 + 优先 TRIVIAL
        scored.sort((a, b) -> {
            int diffCmp = diffPriority(a.log.getDiffLevel()) - diffPriority(b.log.getDiffLevel());
            if (diffCmp != 0) return diffCmp;
            return Double.compare(b.similarity, a.similarity);
        });

        log.debug("[RAG] {} candidates → top {} found for {}", scored.size(),
                Math.min(limit, scored.size()), indexCode);
        return scored.subList(0, Math.min(limit, scored.size()));
    }

    /** 计算相似度: 得分接近 + diff级别奖励 + 理由非空奖励 */
    private double computeSimilarity(EvalIndicatorLog log, BigDecimal targetValue) {
        double score = 0;

        // 得分接近度 (0~50分): 越接近越高
        if (log.getLlmScore() != null && targetValue != null) {
            double diff = Math.abs(log.getLlmScore().doubleValue() - targetValue.doubleValue());
            score += Math.max(0, 50 - diff); // 差 0 分 = 50, 差 50 分 = 0
        }

        // diff 级别奖励: TRIVIAL +30, NOTABLE +15, SIGNIFICANT +0
        String dl = log.getDiffLevel();
        if ("TRIVIAL".equals(dl)) score += 30;
        else if ("NOTABLE".equals(dl)) score += 15;

        // 有理由文本 +10
        if (log.getLlmReason() != null && !log.getLlmReason().isBlank()) score += 10;

        return score;
    }

    /** diff 优先级: TRIVIAL < NOTABLE < SIGNIFICANT */
    private int diffPriority(String level) {
        if ("TRIVIAL".equals(level)) return 0;
        if ("NOTABLE".equals(level)) return 1;
        return 2;
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (v == null) return BigDecimal.ZERO;
        try { return new BigDecimal(v.toString()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    /** 相似案例 */
    public record SimilarCase(EvalIndicatorLog log, double similarity) {
        public String toPromptExample() {
            return String.format("%s=%.1f分, %s",
                    log.getIndexCode() != null ? log.getIndexCode() : "?",
                    log.getLlmScore() != null ? log.getLlmScore() : 0,
                    log.getLlmReason() != null ? log.getLlmReason().substring(0,
                            Math.min(60, log.getLlmReason().length())) : "");
        }
    }
}
