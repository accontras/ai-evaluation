package io.github.accontra.eval.common.exception;

/**
 * 业务异常
 */
public class EvalException extends RuntimeException {

    private final String code;

    public EvalException(String code, String message) {
        super(message);
        this.code = code;
    }

    public EvalException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() { return code; }
}
