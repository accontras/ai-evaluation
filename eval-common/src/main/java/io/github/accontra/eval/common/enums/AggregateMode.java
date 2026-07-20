package io.github.accontra.eval.common.enums;

/**
 * 聚合模式
 */
public enum AggregateMode {
    WEIGHTED_SUM("weighted_sum", "加权求和"),
    WEIGHTED_AVG("weighted_avg", "加权平均"),
    SUM("sum", "求和"),
    MIN("min", "取最小值"),
    SCORE_ACCUMULATE("score_accumulate", "得分累加");

    private final String code;
    private final String desc;

    AggregateMode(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String code() { return code; }
    public String desc() { return desc; }
}
