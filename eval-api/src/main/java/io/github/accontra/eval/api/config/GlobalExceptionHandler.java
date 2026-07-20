package io.github.accontra.eval.api.config;

import io.github.accontra.eval.common.Result;
import io.github.accontra.eval.common.exception.EvalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EvalException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleEvalException(EvalException e) {
        log.warn("业务异常 [{}]: {}", e.code(), e.getMessage());
        return Result.fail(e.code(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail("B0001", "系统内部错误: " + e.getMessage());
    }
}
