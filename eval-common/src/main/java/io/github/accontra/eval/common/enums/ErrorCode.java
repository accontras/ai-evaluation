package io.github.accontra.eval.common.enums;

/**
 * 业务错误码
 */
public enum ErrorCode {

    // 通用
    SUCCESS("00000", "成功"),
    BAD_REQUEST("A0001", "请求参数错误"),
    INTERNAL_ERROR("B0001", "系统内部错误"),

    // 评估 (E = Evaluation)
    SCENE_NOT_FOUND("E0001", "评估方案不存在"),
    SCENE_DISABLED("E0002", "评估方案已禁用"),
    MODEL_NOT_FOUND("E0003", "评估模型不存在"),
    MODEL_DISABLED("E0004", "评估模型已禁用"),
    MODEL_NO_INDICES("E0005", "评估模型未配置指标"),
    TARGET_NOT_FOUND("E0006", "评估对象不存在"),
    TARGET_DISABLED("E0007", "评估对象已禁用"),
    RAW_DATA_EMPTY("E0008", "评估数据为空"),
    ALL_INDICATORS_FAILED("E0009", "全部指标取值失败"),
    STANDARD_NOT_MATCHED("E0010", "未匹配到参考标准"),
    EXPRESSION_EVAL_FAILED("E0011", "表达式求值失败"),
    CYCLE_REFERENCE("E0012", "跨指标循环引用"),
    DIMENSION_NOT_FOUND("E0013", "维度定义不存在"),

    // LLM (L = LLM)
    LLM_CALL_FAILED("L0001", "LLM 调用失败"),
    LLM_RESPONSE_PARSE_FAILED("L0002", "LLM 返回结果解析失败"),
    LLM_DEGRADED("L0003", "LLM 降级为默认评分"),

    // 申诉 (A = Appeal)
    APPEAL_NOT_FOUND("A0001", "申诉记录不存在"),
    APPEAL_STATUS_INVALID("A0002", "申诉状态不允许此操作");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() { return code; }
    public String message() { return message; }
}
