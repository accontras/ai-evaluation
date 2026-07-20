package io.github.accontra.eval.infrastructure.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    @Bean
    @ConfigurationProperties(prefix = "llm")
    public LlmProperties llmProperties() {
        return new LlmProperties();
    }

    @Bean
    public LlmClient llmClient(LlmProperties props) {
        return new LlmClient(props.getBaseUrl(), props.getApiKey(), props.getModel());
    }
}
