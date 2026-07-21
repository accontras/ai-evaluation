package io.github.accontra.eval;

import io.github.accontra.eval.application.event.EventRuleEvaluator;
import io.github.accontra.eval.application.event.LlmEventDetector;
import io.github.accontra.eval.application.handler.*;
import io.github.accontra.eval.application.pipeline.ConfigurablePipeline;
import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.strategy.DualChannelScoringService;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.application.strategy.RuleScoreStrategy;
import io.github.accontra.eval.domain.model.EvalObjectLog;
import io.github.accontra.eval.domain.model.EvalTaskLog;
import io.github.accontra.eval.domain.service.*;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import io.github.accontra.eval.infrastructure.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端测试: H1→H2→H3(双通道)→H4(事件)→H6 全链路。
 */
@SpringBootTest
class EndToEndTest {

    @Autowired private EvalSceneService sceneService;
    @Autowired private EvalModelService modelService;
    @Autowired private EvalModelStageService stageService;
    @Autowired private EvalModelIndexService modelIndexService;
    @Autowired private EvalIndexService indexService;
    @Autowired private LlmScoringStrategy llmStrategy;
    @Autowired private RuleScoreStrategy ruleStrategy;
    @Autowired private DualChannelScoringService dualChannel;
    @Autowired private LlmClient llmClient;
    @Autowired private EvalTaskLogMapper taskLogMapper;
    @Autowired private EvalObjectLogMapper objectLogMapper;
    @Autowired private EvalIndicatorLogMapper indicatorLogMapper;
    @Autowired private EvalModelEventMapper modelEventMapper;
    @Autowired private EvalEventLogMapper eventLogMapper;
    @Autowired private EvalGradeMappingMapper gradeMappingMapper;

    @Test
    void fullPipelineWithRealLlm() {
        // ---- 组装 Handler ----
        var h1 = new ValidateAndLoadModelHandler(sceneService, modelService,
                stageService, modelIndexService, indexService);
        var h2 = new FetchIndicatorValuesHandler();
        var h3 = new LlmCalculateScoresHandler(llmStrategy, ruleStrategy, dualChannel);
        var h4 = new EventRedLineHandler(modelEventMapper, eventLogMapper,
                new EventRuleEvaluator(), new LlmEventDetector(llmClient));
        var h6 = new SummarizeResultHandler(taskLogMapper, objectLogMapper, indicatorLogMapper, gradeMappingMapper, null, null);

        var pipeline = new ConfigurablePipeline(List.of(h1, h2, h3, h4, h6));

        // ---- 准备 Context ----
        var ctx = new EvaluationContext();
        ctx.setSceneCode("LOGISTICS-2026Q2");
        ctx.setBizId("LGS-001");
        ctx.setRawData(Map.of(
                "cost_deviation", 9.2,
                "abnormal_count", 2,
                "fill_rate", 85.5
        ));

        // ---- 执行 ----
        pipeline.execute(ctx);

        // ---- 验证 ----
        assertThat(ctx.getTotalScore()).isNotNull();
        assertThat(ctx.getTotalScore().doubleValue()).isBetween(0.0, 100.0);
        assertThat(ctx.getRiskLevel()).isIn("HIGH", "MEDIUM", "LOW");

        // 验证落库
        List<EvalTaskLog> tasks = taskLogMapper.selectList(null);
        assertThat(tasks).isNotEmpty();
        System.out.printf("✅ task_log.id=%d, status=%s%n", tasks.get(tasks.size() - 1).getId(),
                tasks.get(tasks.size() - 1).getStatus());

        List<EvalObjectLog> objects = objectLogMapper.selectList(null);
        assertThat(objects).isNotEmpty();
        var obj = objects.get(objects.size() - 1);
        System.out.printf("✅ object_log: totalScore=%.1f, riskLevel=%s%n", obj.getTotalScore(), obj.getRiskLevel());
    }
}
