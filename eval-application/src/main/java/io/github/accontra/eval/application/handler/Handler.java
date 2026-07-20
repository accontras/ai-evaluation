package io.github.accontra.eval.application.handler;

import io.github.accontra.eval.application.pipeline.EvaluationContext;

/**
 * Pipeline Handler 接口 — 评估流程中的一个处理步骤。
 * 每个 Handler 只做一件事，输入输出通过 EvaluationContext 传递。
 */
public interface Handler {

    /** 步骤编码，如 VALIDATE / FETCH / CALCULATE */
    String stepCode();

    /** 步骤名称 */
    String stepName();

    /** 执行顺序，越小越先 */
    int order();

    /** 执行 */
    void execute(EvaluationContext ctx);

    /** 是否跳过（默认不跳过） */
    default boolean shouldSkip(EvaluationContext ctx) {
        return false;
    }
}
