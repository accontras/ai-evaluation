package io.github.accontra.eval;

import io.github.accontra.eval.common.util.ExpressionUtil;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JexlTest {

    @Test
    void simpleMath() {
        assertThat(ExpressionUtil.eval("1 + 2").intValue()).isEqualTo(3);
        assertThat(ExpressionUtil.eval("10 * 0.5").doubleValue()).isEqualTo(5.0);
    }

    @Test
    void varReplacement() {
        var resolver = new ExpressionUtil.VarResolver() {
            public Object resolveAttr(String name) {
                return "入司天数".equals(name) ? 73 : null;
            }
            public Object resolveDim(String code) { return null; }
            public Object resolveIdx(String code, String field) { return null; }
        };

        var result = ExpressionUtil.eval("${attr.入司天数} > 30",
                Map.of(), resolver);
        assertThat(result.intValue()).isEqualTo(1); // true
    }

    @Test
    void valAndWeight() {
        Map<String, Object> vars = Map.of("val", 85.5, "weight", 0.7);
        var result = ExpressionUtil.eval("${val} * ${weight}", vars, null);
        assertThat(result.doubleValue()).isCloseTo(59.85, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void boolEval() {
        assertThat(ExpressionUtil.evalBool("1 > 0", null, null)).isTrue();
        assertThat(ExpressionUtil.evalBool("0 > 1", null, null)).isFalse();
    }
}
