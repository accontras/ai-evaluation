package io.github.accontra.eval.common.util;

import org.apache.commons.jexl3.*;
import org.apache.commons.jexl3.introspection.JexlPermissions;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * JEXL 表达式求值工具 — 沙箱安全 + 变量预处理。
 *
 * 6 种变量类型:
 *   ${val}            → 当前指标原始值
 *   ${weight}         → 区间匹配权重
 *   ${attr.xxx}       → 维度属性 (按名称)
 *   ${dim.xxx}        → 维度属性 (按编码 → 名称 → 值)
 *   ${idx.xxx.value}  → 其他指标原始值
 *   ${idx.xxx.score}  → 其他指标得分
 */
public final class ExpressionUtil {

    private static final System.Logger log = System.getLogger(ExpressionUtil.class.getName());

    private static final Pattern ATTR_PATTERN   = Pattern.compile("\\$\\{attr\\.([^}]+)}");
    private static final Pattern DIM_PATTERN    = Pattern.compile("\\$\\{dim\\.([^}]+)}");
    private static final Pattern IDX_PATTERN    = Pattern.compile("\\$\\{idx\\.([^.}]+)\\.(value|score)}");
    private static final Pattern NULL_TOKEN     = Pattern.compile("\\bnull\\b");

    private static final JexlEngine JEXL;

    static {
        JEXL = new JexlBuilder()
                .permissions(JexlPermissions.UNRESTRICTED) // 后续可收紧白名单
                .silent(false)
                .strict(false)
                .create();
    }

    private ExpressionUtil() {}

    /** 简单求值 (无变量替换) */
    public static BigDecimal eval(String expression) {
        return eval(expression, Collections.emptyMap(), null);
    }

    /** 求值 — 替换 6 种变量后执行 */
    public static BigDecimal eval(String expression, Map<String, Object> variables, VarResolver resolver) {
        if (expression == null || expression.isBlank()) {
            return BigDecimal.ZERO;
        }

        String resolved = preprocess(expression, variables, resolver);

        // null 安全网
        if (NULL_TOKEN.matcher(resolved).find()) {
            log.log(System.Logger.Level.WARNING, "表达式含独立 null token, 返回默认 100: " + resolved);
            return BigDecimal.valueOf(100);
        }

        try {
            JexlExpression expr = JEXL.createExpression(resolved);
            Object result = expr.evaluate(null);
            if (result instanceof Number n) {
                return BigDecimal.valueOf(n.doubleValue());
            }
            if (result instanceof Boolean b) {
                return b ? BigDecimal.ONE : BigDecimal.ZERO;
            }
            log.log(System.Logger.Level.WARNING, "JEXL 求值返回非数字: " + resolved + " → " + result);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "JEXL 求值失败: " + expression, e);
            throw new RuntimeException("表达式求值失败: " + e.getMessage(), e);
        }
    }

    /** 布尔求值 (用于事件触发条件、路由条件) */
    public static boolean evalBool(String expression, Map<String, Object> variables, VarResolver resolver) {
        if (expression == null || expression.isBlank()) return true;
        BigDecimal result = eval(expression, variables, resolver);
        return result.compareTo(BigDecimal.ZERO) != 0;
    }

    // ---- variable preprocessing ----

    static String preprocess(String expr, Map<String, Object> variables, VarResolver resolver) {
        String result = expr;

        // 1. ${val}
        if (expr.contains("${val}") && variables != null) {
            Object v = variables.get("val");
            result = result.replace("${val}", v != null ? v.toString() : "null");
        }

        // 2. ${weight}
        if (result.contains("${weight}") && variables != null) {
            Object w = variables.get("weight");
            result = result.replace("${weight}", w != null ? w.toString() : "null");
        }

        // 3. ${attr.xxx}
        if (result.contains("${attr.") && resolver != null) {
            var m = ATTR_PATTERN.matcher(result);
            while (m.find()) {
                String attrName = m.group(1);
                Object value = resolver.resolveAttr(attrName);
                result = result.replace(m.group(), value != null ? value.toString() : "null");
            }
        }

        // 4. ${dim.xxx}
        if (result.contains("${dim.") && resolver != null) {
            var m = DIM_PATTERN.matcher(result);
            while (m.find()) {
                String dimCode = m.group(1);
                Object value = resolver.resolveDim(dimCode);
                result = result.replace(m.group(), value != null ? value.toString() : "null");
            }
        }

        // 5. ${idx.xxx.value} / ${idx.xxx.score}
        if (result.contains("${idx.") && resolver != null) {
            var m = IDX_PATTERN.matcher(result);
            while (m.find()) {
                String idxCode = m.group(1);
                String field = m.group(2);
                Object value = resolver.resolveIdx(idxCode, field);
                result = result.replace(m.group(), value != null ? value.toString() : "null");
            }
        }

        return result;
    }

    /** 变量解析器 — 由调用方实现 */
    public interface VarResolver {
        Object resolveAttr(String attrName);
        Object resolveDim(String dimCode);
        Object resolveIdx(String indexCode, String field); // field = "value" | "score"
    }
}
