package io.github.accontra.eval.application.service;

import io.github.accontra.eval.application.event.EventRuleEvaluator;
import io.github.accontra.eval.application.event.LlmEventDetector;
import io.github.accontra.eval.application.handler.*;
import io.github.accontra.eval.application.pipeline.ConfigurablePipeline;
import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.strategy.DualChannelScoringService;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.application.strategy.RuleScoreStrategy;
import io.github.accontra.eval.domain.service.EvalIndexService;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import io.github.accontra.eval.infrastructure.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 评估执行编排 — DomainService 层。
 *
 * 职责: Pipeline 组装、评估执行、排名、对比统计。
 * Controller 只做参数校验 + Result 组装。
 */
@Component
public class EvaluationDomainService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationDomainService.class);

    // H1-H6 依赖 (符合 DomainService → Mapper/Service ✅)
    private final ModelConfigCache configCache;
    private final EvalIndexService indexService;
    private final LlmScoringStrategy llmStrategy;
    private final RuleScoreStrategy ruleStrategy;
    private final DualChannelScoringService dualChannel;
    private final LlmClient llmClient;
    private final EvalTaskLogMapper taskLogMapper;
    private final EvalObjectLogMapper objectLogMapper;
    private final EvalIndicatorLogMapper indicatorLogMapper;
    private final EvalModelEventMapper modelEventMapper;
    private final EvalEventLogMapper eventLogMapper;
    private final EvalGradeMappingMapper gradeMappingMapper;

    // 辅助服务
    private final RankingService rankingService;
    private final MultiModelCompareService multiModelService;
    private final AiSummaryService summaryService;
    private final EvalAiExperimentMapper experimentMapper;

    public EvaluationDomainService(ModelConfigCache configCache, EvalIndexService indexService,
                                    LlmScoringStrategy llmStrategy, RuleScoreStrategy ruleStrategy,
                                    DualChannelScoringService dualChannel, LlmClient llmClient,
                                    EvalTaskLogMapper taskLogMapper, EvalObjectLogMapper objectLogMapper,
                                    EvalIndicatorLogMapper indicatorLogMapper,
                                    EvalModelEventMapper modelEventMapper, EvalEventLogMapper eventLogMapper,
                                    EvalGradeMappingMapper gradeMappingMapper,
                                    RankingService rankingService, MultiModelCompareService multiModelService,
                                    AiSummaryService summaryService, EvalAiExperimentMapper experimentMapper) {
        this.configCache = configCache;
        this.indexService = indexService;
        this.llmStrategy = llmStrategy;
        this.ruleStrategy = ruleStrategy;
        this.dualChannel = dualChannel;
        this.llmClient = llmClient;
        this.taskLogMapper = taskLogMapper;
        this.objectLogMapper = objectLogMapper;
        this.indicatorLogMapper = indicatorLogMapper;
        this.modelEventMapper = modelEventMapper;
        this.eventLogMapper = eventLogMapper;
        this.gradeMappingMapper = gradeMappingMapper;
        this.rankingService = rankingService;
        this.multiModelService = multiModelService;
        this.summaryService = summaryService;
        this.experimentMapper = experimentMapper;
    }

    /** 执行单对象评估 */
    public EvaluationContext execute(String sceneCode, String bizId, String dataPeriod,
                                      Map<String, Object> rawData) {
        var ctx = new EvaluationContext();
        ctx.setSceneCode(sceneCode);
        ctx.setBizId(bizId);
        ctx.setDataPeriod(dataPeriod);
        ctx.setRawData(rawData);
        buildPipeline().execute(ctx);
        return ctx;
    }

    /** 多模型对比 */
    public MultiModelCompareService.CompareResult compareModels(EvaluationContext ctx) {
        return multiModelService.compare(ctx);
    }

    /** 双通道对比统计 */
    public Map<String, Object> compareStats() {
        var logs = indicatorLogMapper.selectList(null);
        if (logs == null || logs.isEmpty()) return Map.of("message", "暂无对比数据");
        var withDiff = logs.stream().filter(l -> l.getDiffLevel() != null).toList();
        long total = withDiff.size();
        long sig = withDiff.stream().filter(l -> "SIGNIFICANT".equals(l.getDiffLevel())).count();
        long notable = withDiff.stream().filter(l -> "NOTABLE".equals(l.getDiffLevel())).count();
        double avgDiff = withDiff.stream().filter(l -> l.getScoreDiff() != null)
                .mapToDouble(l -> l.getScoreDiff().doubleValue()).average().orElse(0);
        return Map.of("totalCompared", total, "significantCount", sig, "notableCount", notable,
                "trivialCount", total - sig - notable,
                "significantRate", total > 0 ? String.format("%.1f%%", 100.0 * sig / total) : "0%",
                "avgDiff", String.format("%.2f", avgDiff));
    }

    /** 奥运排名 */
    public int rank(String sceneCode) { return rankingService.rank(sceneCode); }

    /** AI 总结 */
    public String generateSummary(Long objectLogId) { return summaryService.generateSummary(objectLogId); }

    // ---- Pipeline 组装 ----

    private ConfigurablePipeline buildPipeline() {
        var h1 = new ValidateAndLoadModelHandler(configCache, indexService);
        var h2 = new FetchIndicatorValuesHandler();
        var h3 = new LlmCalculateScoresHandler(llmStrategy, ruleStrategy, dualChannel);
        var h4 = new EventRedLineHandler(modelEventMapper, eventLogMapper,
                new EventRuleEvaluator(), new LlmEventDetector(llmClient));
        var h6 = new SummarizeResultHandler(taskLogMapper, objectLogMapper, indicatorLogMapper, gradeMappingMapper);
        return new ConfigurablePipeline(List.of(h1, h2, h3, h4, h6));
    }
}
