package io.github.accontra.eval.infrastructure.llm;

import java.util.Map;

/**
 * Prompt 模板 — 支持 {{variable}} 占位符替换。
 *
 * 用法:
 *   var tpl = PromptTemplate.of("请对 {{indicatorName}} 打分，当前值为 {{rawValue}}。");
 *   var prompt = tpl.render(Map.of("indicatorName", "日志填报率", "rawValue", "85.5"));
 */
public class PromptTemplate {

    private final String template;

    private PromptTemplate(String template) {
        this.template = template;
    }

    public static PromptTemplate of(String template) {
        return new PromptTemplate(template);
    }

    /**
     * 用 {{key}} 替换模板中的占位符。
     */
    public String render(Map<String, Object> variables) {
        String result = template;
        for (var entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }
}
