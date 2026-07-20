package io.github.accontra.eval;

import io.github.accontra.eval.application.handler.PingHandler;
import io.github.accontra.eval.application.pipeline.ConfigurablePipeline;
import io.github.accontra.eval.application.pipeline.EvaluationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineTest {

    @Test
    void pingHandlerShouldExecute() {
        var ctx = new EvaluationContext();
        ctx.setSceneCode("TEST-SCENE");
        ctx.setBizId("BIZ-001");

        var pipeline = new ConfigurablePipeline(List.of(new PingHandler()));
        pipeline.execute(ctx); // 不抛异常即通过

        assertThat(ctx.getSceneCode()).isEqualTo("TEST-SCENE");
    }
}
