package io.github.accontra.eval.common.enums;

/**
 * Stage 节点类型
 */
public enum StageType {
    TOP("top", "路由层"),
    NORMAL("normal", "中间维度"),
    LEAF("leaf", "叶子维度");

    private final String code;
    private final String desc;

    StageType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String code() { return code; }
    public String desc() { return desc; }
}
