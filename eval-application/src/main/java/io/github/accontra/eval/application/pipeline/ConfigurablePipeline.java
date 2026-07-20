package io.github.accontra.eval.application.pipeline;

import io.github.accontra.eval.application.handler.Handler;
import io.github.accontra.eval.common.exception.EvalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

/**
 * Pipeline 调度器 — 按固定顺序执行 Handler 链。
 * Handler 顺序由 order() 决定，在构造时一次性排序。
 */
public class ConfigurablePipeline {

    private static final Logger log = LoggerFactory.getLogger(ConfigurablePipeline.class);

    private final List<Handler> handlers;

    public ConfigurablePipeline(List<Handler> handlers) {
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt(Handler::order))
                .toList();
        log.info("Pipeline initialized with {} handlers: {}",
                handlers.size(),
                handlers.stream().map(h -> h.stepCode() + "(" + h.order() + ")").toList());
    }

    /**
     * 顺序执行全部 Handler，单个 Handler 失败则中断并抛异常。
     */
    public void execute(EvaluationContext ctx) {
        for (Handler handler : handlers) {
            if (handler.shouldSkip(ctx)) {
                log.info("[{}] skipped", handler.stepName());
                continue;
            }
            log.info("[{}] executing...", handler.stepName());
            try {
                handler.execute(ctx);
                log.info("[{}] done", handler.stepName());
            } catch (Exception e) {
                log.error("[{}] failed: {}", handler.stepName(), e.getMessage(), e);
                throw new EvalException(handler.stepCode(), "Handler 执行失败: " + e.getMessage(), e);
            }
        }
    }
}
