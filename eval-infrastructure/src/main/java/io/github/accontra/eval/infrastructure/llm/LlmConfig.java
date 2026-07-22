package io.github.accontra.eval.infrastructure.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    @ConfigurationProperties(prefix = "llm")
    public LlmProperties llmProperties() { return new LlmProperties(); }

    @Bean
    @Primary
    public ResilientLlmClient resilientLlmClient(LlmProperties props) {
        var ep = props.effectivePrimary();
        var primary = newClient(ep);

        List<LlmClient> fallbackClients = new ArrayList<>();
        for (var fc : props.getFallbacks()) {
            fallbackClients.add(newClient(fc));
        }

        int retries = props.getRetry().getMaxRetries();
        int threshold = props.getCircuit().getThreshold();
        long halfOpenMs = props.getCircuit().getHalfOpenMs();
        int tokenBudget = props.getTokenBudget().getMaxPerEval();

        log.info("[LLM] ResilientClient: primary={}, fallbacks={}, retries={}, threshold={}, budget={}",
                ep.getModel(),
                props.getFallbacks().stream().map(LlmProperties.ModelConfig::getModel).toList(),
                retries, threshold, tokenBudget);

        return new ResilientLlmClient(primary, fallbackClients, retries, threshold, halfOpenMs, tokenBudget);
    }

    private LlmClient newClient(LlmProperties.ModelConfig cfg) {
        return new OpenAiCompatibleLlmClient(
                cfg.getBaseUrl(), cfg.getApiKey(), cfg.getModel(), cfg.getTemperature());
    }
}
