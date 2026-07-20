package io.github.accontra.eval;

import io.github.accontra.eval.infrastructure.llm.PromptTemplate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmTest {

    @Test
    void promptTemplateShouldReplaceVariables() {
        var tpl = PromptTemplate.of("请对 {{name}} 打分，当前值为 {{value}}。");

        var result = tpl.render(Map.of("name", "日志填报率", "value", "85.5"));

        assertThat(result).isEqualTo("请对 日志填报率 打分，当前值为 85.5。");
        assertThat(result).doesNotContain("{{");
    }

    @Test
    void promptTemplateShouldHandleNull() {
        var tpl = PromptTemplate.of("{{a}}{{b}}");
        var result = tpl.render(Map.of("a", "x"));
        assertThat(result).contains("x");
    }
}
