package io.github.accontra.eval;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.strategy.DualChannelScoringService;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.application.strategy.RuleScoreStrategy;
import io.github.accontra.eval.domain.model.EvalIndex;
import io.github.accontra.eval.domain.model.EvalModelIndex;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DualChannelTest {

    @Autowired private LlmClient llmClient;

    @Test
    void dualChannelCompare() {
        var ctx = new EvaluationContext();
        ctx.setBizId("LGS-001");
        ctx.setRawValues(Map.of(
                "cost_deviation", 9.2,
                "abnormal_count", 2,
                "fill_rate", 85.5
        ));

        var ib1 = new EvalIndex(); ib1.setId(1L); ib1.setCode("COST_DEV"); ib1.setName("费用偏差率");
        var ib2 = new EvalIndex(); ib2.setId(2L); ib2.setCode("ABNORM_CNT"); ib2.setName("异常波动次数");
        var ib3 = new EvalIndex(); ib3.setId(3L); ib3.setCode("FILL_RATE"); ib3.setName("填报及时率");
        ctx.setIndexBaseMap(Map.of("1", ib1, "2", ib2, "3", ib3));

        var mi1 = new EvalModelIndex(); mi1.setIndexId(1L);
        var mi2 = new EvalModelIndex(); mi2.setIndexId(2L);
        var mi3 = new EvalModelIndex(); mi3.setIndexId(3L);
        ctx.setModelIndices(List.of(mi1, mi2, mi3));

        // 双通道
        var llmStrategy = new LlmScoringStrategy(llmClient);
        var ruleStrategy = new RuleScoreStrategy();
        var dual = new DualChannelScoringService(llmStrategy, ruleStrategy);

        var result = dual.compare(ctx);

        assertThat(result.diffs()).isNotEmpty();
        System.out.println("\n===== LLM vs 规则引擎 对比 =====");
        for (var d : result.diffs()) {
            System.out.printf("  %s (%s): LLM=%.0f 规则=%.0f 差异=%.0f [%s]%n",
                    d.indexName(), d.indexCode(),
                    d.llmScore(), d.ruleScore() != null ? d.ruleScore() : 0,
                    d.diff(), d.diffLevel());
            System.out.printf("    LLM: %s%n", d.llmReason());
        }
        System.out.printf("  SIG=%d NOTABLE=%d TRIVIAL=%d%n",
                result.significantCount(), result.notableCount(), result.trivialCount());
    }
}
