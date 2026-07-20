package io.github.accontra.eval.common;

/**
 * 统一 API 响应
 */
public record Result<T>(String code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>("00000", "成功", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>("00000", "成功", null);
    }

    public static <T> Result<T> fail(String code, String message) {
        return new Result<>(code, message, null);
    }
}
