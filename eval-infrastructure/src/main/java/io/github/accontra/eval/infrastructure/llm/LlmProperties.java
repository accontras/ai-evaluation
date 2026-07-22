package io.github.accontra.eval.infrastructure.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;
import java.util.ArrayList;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** A4: 主模型配置 */
    private ModelConfig primary = new ModelConfig();

    /** A4: 备选模型列表 */
    private List<ModelConfig> fallbacks = new ArrayList<>();

    /** A4: 重试配置 */
    private RetryConfig retry = new RetryConfig();

    /** A4: 熔断配置 */
    private CircuitConfig circuit = new CircuitConfig();

    /** A4: token 预算 */
    private TokenBudgetConfig tokenBudget = new TokenBudgetConfig();

    // ======== 旧字段 (兼容，映射到 primary) ========

    /** @deprecated 使用 primary.provider */
    @Deprecated
    private String provider = "deepseek";

    /** @deprecated 使用 primary.apiKey */
    @Deprecated
    private String apiKey;

    /** @deprecated 使用 primary.baseUrl */
    @Deprecated
    private String baseUrl = "https://api.deepseek.com";

    /** @deprecated 使用 primary.model */
    @Deprecated
    private String model = "deepseek-chat";

    // ======== 内部配置类 ========

    public static class ModelConfig {
        private String provider = "deepseek";
        private String apiKey;
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-chat";
        private double temperature = 0.3;

        public String getProvider() { return provider; }
        public void setProvider(String v) { provider = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { apiKey = v; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { baseUrl = v; }
        public String getModel() { return model; }
        public void setModel(String v) { model = v; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double v) { temperature = v; }
    }

    public static class RetryConfig {
        private int maxRetries = 1;
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int v) { maxRetries = v; }
    }

    public static class CircuitConfig {
        private int threshold = 5;
        private long halfOpenMs = 30_000;
        public int getThreshold() { return threshold; }
        public void setThreshold(int v) { threshold = v; }
        public long getHalfOpenMs() { return halfOpenMs; }
        public void setHalfOpenMs(long v) { halfOpenMs = v; }
    }

    public static class TokenBudgetConfig {
        private int maxPerEval = 8000;
        public int getMaxPerEval() { return maxPerEval; }
        public void setMaxPerEval(int v) { maxPerEval = v; }
    }

    // ======== getter/setter ========

    public ModelConfig getPrimary() { return primary; }
    public void setPrimary(ModelConfig v) { primary = v; }

    public List<ModelConfig> getFallbacks() { return fallbacks; }
    public void setFallbacks(List<ModelConfig> v) { fallbacks = v; }

    public RetryConfig getRetry() { return retry; }
    public void setRetry(RetryConfig v) { retry = v; }

    public CircuitConfig getCircuit() { return circuit; }
    public void setCircuit(CircuitConfig v) { circuit = v; }

    public TokenBudgetConfig getTokenBudget() { return tokenBudget; }
    public void setTokenBudget(TokenBudgetConfig v) { tokenBudget = v; }

    // 旧字段 getter/setter (兼容)
    @Deprecated public String getProvider() { return provider; }
    @Deprecated public void setProvider(String v) { provider = v; }
    @Deprecated public String getApiKey() { return apiKey; }
    @Deprecated public void setApiKey(String v) { apiKey = v; }
    @Deprecated public String getBaseUrl() { return baseUrl; }
    @Deprecated public void setBaseUrl(String v) { baseUrl = v; }
    @Deprecated public String getModel() { return model; }
    @Deprecated public void setModel(String v) { model = v; }

    /** A4: 解析 effective primary 配置（优先新字段，fallback 旧字段） */
    public ModelConfig effectivePrimary() {
        if (primary.getApiKey() != null) return primary;
        primary.setProvider(provider);
        primary.setApiKey(apiKey);
        primary.setBaseUrl(baseUrl);
        primary.setModel(model);
        return primary;
    }
}
