package io.github.accontra.eval.common.enums;

/**
 * 参考标准类型 — 决定用规则引擎还是 AI 来执行评分。
 */
public enum StandardType {
    /** 结构化规则: 条件树 → JEXL → 区间/字典匹配, RuleScoreStrategy */
    STRUCTURED("STRUCTURED", "结构化规则"),
    /** 表达式规则: 手写 JEXL 表达式, RuleScoreStrategy */
    EXPRESSION("EXPRESSION", "表达式规则"),
    /** AI 判断: LLM-as-Judge, LlmScoringStrategy */
    AI("AI", "AI 判断"),
    /** 兜底固定值: 所有其他标准未命中时的固定分值 */
    DEFAULT_FIXED("DEFAULT_FIXED", "兜底固定值"),
    /** 兜底表达式: 所有其他标准未命中时的表达式兜底 */
    DEFAULT_EXPR("DEFAULT_EXPR", "兜底表达式");

    private final String code;
    private final String desc;

    StandardType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String code() { return code; }
    public String desc() { return desc; }

    /** 是否需要规则引擎执行 */
    public boolean isRuleBased() {
        return this == STRUCTURED || this == EXPRESSION
                || this == DEFAULT_FIXED || this == DEFAULT_EXPR;
    }

    /** 是否需要 LLM 执行 */
    public boolean isAiBased() {
        return this == AI;
    }

    /** 是否为兜底类型 */
    public boolean isFallback() {
        return this == DEFAULT_FIXED || this == DEFAULT_EXPR;
    }
}
