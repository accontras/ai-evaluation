package io.github.accontra.eval.application.service;

import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import io.github.accontra.eval.infrastructure.mapper.EvalAiExperimentMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalIndicatorLogMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalModelStandardMapper;
import io.github.accontra.eval.infrastructure.rag.VectorRagService;
import org.springframework.stereotype.Component;

@Component
public class ScoringStrategyFactory {

    private final EvalModelStandardMapper standardMapper;
    private final EvalIndicatorLogMapper indicatorLogMapper;
    private final EvalAiExperimentMapper experimentMapper;
    private final PromptTemplateService promptService;
    private final SimilarCaseService similarCaseService;
    private final VectorRagService vectorRagService;
    private final RagCompareTracker ragCompareTracker;

    public ScoringStrategyFactory(EvalModelStandardMapper standardMapper,
                                   EvalIndicatorLogMapper indicatorLogMapper,
                                   EvalAiExperimentMapper experimentMapper,
                                   PromptTemplateService promptService,
                                   SimilarCaseService similarCaseService,
                                   VectorRagService vectorRagService,
                                   RagCompareTracker ragCompareTracker) {
        this.standardMapper = standardMapper;
        this.indicatorLogMapper = indicatorLogMapper;
        this.experimentMapper = experimentMapper;
        this.promptService = promptService;
        this.similarCaseService = similarCaseService;
        this.vectorRagService = vectorRagService;
        this.ragCompareTracker = ragCompareTracker;
    }

    public LlmScoringStrategy create(LlmClient client) {
        return new LlmScoringStrategy(client, standardMapper,
                indicatorLogMapper, experimentMapper, promptService,
                similarCaseService, vectorRagService, ragCompareTracker);
    }
}
