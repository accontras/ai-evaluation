package io.github.accontra.eval.common.enums;

/**
 * 评估事件类型
 */
public enum EventType {
    RED_LINE("RED_LINE", "红线"),
    MARK("MARK", "标记"),
    BONUS("BONUS", "加分"),
    DEDUCT("DEDUCT", "扣分");

    private final String code;
    private final String desc;

    EventType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String code() { return code; }
    public String desc() { return desc; }
}
