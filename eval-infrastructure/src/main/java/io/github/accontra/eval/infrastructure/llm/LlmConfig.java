package io.github.accontra.eval.infrastructure.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * LLM Bean 配置 — A1.1 多模型基础设施。
 *
 * 注册 3 个 LlmClient Bean (deepseek / glm / qwen)。
 * 当前所有 Bean 共用同一个 api-key 和 DeepSeek endpoint ——
 * 接入真实 GLM/Qwen key 时只需改 application.yml。
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    @ConfigurationProperties(prefix = "llm")
    public LlmProperties llmProperties() {
        return new LlmProperties();
    }

    @Bean
    @Primary
    public LlmClient deepseekClient(LlmProperties props) {
        log.info("[LLM] deepseek: baseUrl={}, model={}", props.getBaseUrl(), props.getModel());
        return new OpenAiCompatibleLlmClient(props.getBaseUrl(), props.getApiKey(), props.getModel(), 0.3);
    }

    @Bean("glm")
    public LlmClient glmClient(LlmProperties props) {
        // TODO: 替换为 GLM 的真实 baseUrl + model 后，使用独立的 api-key
        log.info("[LLM] glm (暂用 DeepSeek): baseUrl={}, model={}", props.getBaseUrl(), props.getModel());
        return new OpenAiCompatibleLlmClient(props.getBaseUrl(), props.getApiKey(), props.getModel(), 0.3);
    }

    @Bean("qwen")
    public LlmClient qwenClient(LlmProperties props) {
        // TODO: 替换为 Qwen 的真实 baseUrl + model 后，使用独立的 api-key
        log.info("[LLM] qwen (暂用 DeepSeek): baseUrl={}, model={}", props.getBaseUrl(), props.getModel());
        return new OpenAiCompatibleLlmClient(props.getBaseUrl(), props.getApiKey(), props.getModel(), 0.3);
    }
}
