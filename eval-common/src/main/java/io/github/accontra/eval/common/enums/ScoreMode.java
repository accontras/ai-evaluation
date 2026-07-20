package io.github.accontra.eval.common.enums;

/**
 * 得分计算方式
 */
public enum ScoreMode {
    RAW_WEIGHT("RAW_WEIGHT", "原始值×权重"),
    FIXED("FIXED", "固定分值"),
    FIXED_WEIGHT("FIXED_WEIGHT", "固定分值×权重"),
    INTERVAL_WEIGHT("INTERVAL_WEIGHT", "区间权重");

    private final String code;
    private final String desc;

    ScoreMode(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String code() { return code; }
    public String desc() { return desc; }
}
