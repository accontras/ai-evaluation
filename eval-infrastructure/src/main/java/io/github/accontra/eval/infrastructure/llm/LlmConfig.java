package io.github.accontra.eval.infrastructure.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
    public LlmClient llmClient(LlmProperties props) {
        log.info("LLM config: provider={}, baseUrl={}, model={}, apiKey={}...",
                props.getProvider(), props.getBaseUrl(), props.getModel(),
                props.getApiKey() != null && !props.getApiKey().isEmpty()
                        ? props.getApiKey().substring(0, Math.min(8, props.getApiKey().length())) : "EMPTY");
        return new LlmClient(props.getBaseUrl(), props.getApiKey(), props.getModel());
    }

    @Bean("llmClientLowTemp")
    public LlmClient llmClientLowTemp(LlmProperties props) {
        log.info("LLM alt config: t=0.1, model={}", props.getModel());
        return new LlmClient(props.getBaseUrl(), props.getApiKey(), props.getModel(), 0.1);
    }
}
