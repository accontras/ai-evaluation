package io.github.accontra.eval;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
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
class LlmScoringTest {

    @Autowired
    private LlmClient llmClient;

    @Test
    void deepseekShouldScoreIndicators() {
        var strategy = new LlmScoringStrategy(llmClient);

        // 模拟: 物流费用合理性评估，3个指标
        var ctx = new EvaluationContext();
        ctx.setBizId("LGS-001");
        ctx.setRawValues(Map.of(
                "COST_DEV", 9.2,      // 费用偏差率 9.2%
                "ABNORM_CNT", 2,       // 异常波动 2次
                "FILL_RATE", 85.5      // 填报及时率 85.5%
        ));

        // 指标定义
        var ib1 = new EvalIndex(); ib1.setId(1L); ib1.setCode("COST_DEV"); ib1.setName("费用偏差率");
        var ib2 = new EvalIndex(); ib2.setId(2L); ib2.setCode("ABNORM_CNT"); ib2.setName("异常波动次数");
        var ib3 = new EvalIndex(); ib3.setId(3L); ib3.setCode("FILL_RATE"); ib3.setName("填报及时率");
        ctx.setIndexBaseMap(Map.of("1", ib1, "2", ib2, "3", ib3));

        var mi1 = new EvalModelIndex(); mi1.setIndexId(1L);
        var mi2 = new EvalModelIndex(); mi2.setIndexId(2L);
        var mi3 = new EvalModelIndex(); mi3.setIndexId(3L);
        ctx.setModelIndices(List.of(mi1, mi2, mi3));

        // ★ 真实调用 DeepSeek
        var results = strategy.scoreAll(ctx);

        // 验证
        assertThat(results).hasSize(3);
        assertThat(results).containsKeys("COST_DEV", "ABNORM_CNT", "FILL_RATE");

        for (var r : results.values()) {
            assertThat(r.score().doubleValue()).isBetween(0.0, 100.0);
            assertThat(r.reason()).isNotBlank();
            System.out.printf("  %s (%s): %.1f分 — %s%n",
                    r.indexName(), r.indexCode(), r.score(), r.reason());
        }
    }
}
