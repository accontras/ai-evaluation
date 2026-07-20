package io.github.accontra.eval.common.enums;

/**
 * 申诉类型
 */
public enum AppealType {
    BONUS("BONUS", "加分申诉"),
    PENALTY("PENALTY", "减分申诉"),
    TOTAL("TOTAL", "总分申诉"),
    DIMENSION("DIMENSION", "维度申诉");

    private final String code;
    private final String desc;

    AppealType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String code() { return code; }
    public String desc() { return desc; }
}
