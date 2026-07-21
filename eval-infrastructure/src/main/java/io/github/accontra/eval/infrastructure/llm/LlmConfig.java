package io.github.accontra.eval.infrastructure.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    @ConfigurationProperties(prefix = "llm")
    public LlmProperties llmProperties() { return new LlmProperties(); }

    @Bean("deepseek")
    public LlmClient deepseekClient(LlmProperties props) {
        return new OpenAiCompatibleLlmClient(props.getBaseUrl(), props.getApiKey(), props.getModel(), 0.3);
    }

    @Bean("glm")
    public LlmClient glmClient(LlmProperties props) {
        return new OpenAiCompatibleLlmClient(props.getBaseUrl(), props.getApiKey(), props.getModel(), 0.3);
    }

    @Bean("qwen")
    public LlmClient qwenClient(LlmProperties props) {
        return new OpenAiCompatibleLlmClient(props.getBaseUrl(), props.getApiKey(), props.getModel(), 0.3);
    }

    @Bean
    @Primary
    public ResilientLlmClient resilientLlmClient(LlmProperties props) {
        var primary = new OpenAiCompatibleLlmClient(props.getBaseUrl(), props.getApiKey(), props.getModel(), 0.3);
        var glm = new OpenAiCompatibleLlmClient(props.getBaseUrl(), props.getApiKey(), props.getModel(), 0.3);
        var qwen = new OpenAiCompatibleLlmClient(props.getBaseUrl(), props.getApiKey(), props.getModel(), 0.3);
        log.info("[LLM] ResilientClient: primary={}, fallbacks=[glm,qwen], retries=1, circuitThreshold=5", props.getModel());
        return new ResilientLlmClient(primary, List.of(glm, qwen), 1, 5);
    }
}
