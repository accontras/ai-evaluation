package io.github.accontra.eval.application.handler;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 测试 Handler — 只打日志，验证 Pipeline 链路通畅。
 */
public class PingHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(PingHandler.class);

    @Override
    public String stepCode() { return "PING"; }

    @Override
    public String stepName() { return "Pipeline连通性测试"; }

    @Override
    public int order() { return 0; }

    @Override
    public void execute(EvaluationContext ctx) {
        log.info("Pipeline is alive! sceneCode={}, bizId={}", ctx.getSceneCode(), ctx.getBizId());
    }
}
