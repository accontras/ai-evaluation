package io.github.accontra.eval.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.accontra.eval.application.service.PromptTemplateService;
import io.github.accontra.eval.application.service.SimilarCaseService;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import io.github.accontra.eval.infrastructure.mapper.EvalAiExperimentMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalIndicatorLogMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalModelStandardMapper;
import io.github.accontra.eval.infrastructure.rag.VectorRagService;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 应用配置 — Caffeine 缓存 + Strategy Bean。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(200)
                .recordStats());
        return manager;
    }

    @Bean
    public LlmScoringStrategy llmScoringStrategy(LlmClient llmClient,
                                                   EvalModelStandardMapper standardMapper,
                                                   EvalIndicatorLogMapper indicatorLogMapper,
                                                   EvalAiExperimentMapper experimentMapper,
                                                   PromptTemplateService promptService,
                                                   SimilarCaseService similarCaseService,
                                                   VectorRagService vectorRagService) {
        return new LlmScoringStrategy(llmClient, standardMapper, indicatorLogMapper, experimentMapper, promptService, similarCaseService, vectorRagService);
    }
}
