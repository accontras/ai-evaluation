package io.github.accontra.eval.application.service;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.application.strategy.LlmScoringStrategy;
import io.github.accontra.eval.application.strategy.RuleScoreStrategy;
import io.github.accontra.eval.infrastructure.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MultiModelCompareServiceTest {

    private Map<String, LlmClient> clients;
    private ScoringStrategyFactory factory;
    private RuleScoreStrategy ruleStrategy;
    private MultiModelCompareService service;

    @BeforeEach
    void setUp() {
        clients = new LinkedHashMap<>();
        factory = mock(ScoringStrategyFactory.class);
        ruleStrategy = mock(RuleScoreStrategy.class);
        service = new MultiModelCompareService(clients, factory, ruleStrategy);
    }

    private static LlmClient mockClient(String model) {
        var client = mock(LlmClient.class);
        when(client.getModel()).thenReturn(model);
        return client;
    }

    private static LlmScoringStrategy.ScoreResult score(String code, String name, double score, String reason) {
        return new LlmScoringStrategy.ScoreResult(code, name, BigDecimal.valueOf(score), reason);
    }

    /** 5.1 双模型对比 — inject 2 clients → 2 results, crossModelVariance > 0 */
    @Test
    void compareWithTwoModelsReturnsBothResults() {
        var clientA = mockClient("model-a");
        var clientB = mockClient("model-b");
        clients.put("a", clientA);
        clients.put("b", clientB);

        var stratA = mock(LlmScoringStrategy.class);
        var stratB = mock(LlmScoringStrategy.class);
        when(factory.create(clientA)).thenReturn(stratA);
        when(factory.create(clientB)).thenReturn(stratB);
        when(stratA.scoreAll(any())).thenReturn(Map.of(
                "CODE1", score("CODE1", "指标1", 80, "ok"),
                "CODE2", score("CODE2", "指标2", 90, "good")
        ));
        when(stratB.scoreAll(any())).thenReturn(Map.of(
                "CODE1", score("CODE1", "指标1", 70, "fair"),
                "CODE2", score("CODE2", "指标2", 85, "ok")
        ));

        var result = service.compare(new EvaluationContext(), 1);

        assertEquals(2, result.availableModelCount());
        assertEquals(2, result.totalModelCount());
        assertTrue(result.crossModelVariance().containsKey("CODE1"));
        assertTrue(result.crossModelVariance().containsKey("CODE2"));
        assertTrue(result.crossModelVariance().get("CODE1") > 0);
        assertTrue(result.crossModelVariance().get("CODE2") > 0);
    }

    /** 5.2 单模型降级 — inject 1 client → availableModelCount=1 */
    @Test
    void compareWithSingleModelReturnsOneResult() {
        var client = mockClient("model-a");
        clients.put("a", client);

        var strategy = mock(LlmScoringStrategy.class);
        when(factory.create(client)).thenReturn(strategy);
        when(strategy.scoreAll(any())).thenReturn(Map.of(
                "CODE1", score("CODE1", "指标1", 85, "ok")
        ));

        var result = service.compare(new EvaluationContext(), 1);

        assertEquals(1, result.availableModelCount());
        assertEquals(1, result.totalModelCount());
        assertEquals(1, result.modelScores().size());
        assertTrue(result.modelScores().containsKey("a"));
    }

    /** 5.3 稳定性方差 — inject 1 client, repeatCount=3, 3 次不同分数 → stdDev 正确 */
    @Test
    void stabilityVarianceWithRepeatedScores() {
        var client = mockClient("model-a");
        clients.put("a", client);

        var strategy = mock(LlmScoringStrategy.class);
        when(factory.create(client)).thenReturn(strategy);
        when(strategy.scoreAll(any())).thenReturn(
                Map.of("CODE1", score("CODE1", "指标1", 80, "run1")),
                Map.of("CODE1", score("CODE1", "指标1", 90, "run2")),
                Map.of("CODE1", score("CODE1", "指标1", 100, "run3"))
        );

        var result = service.compare(new EvaluationContext(), 3);

        assertEquals(1, result.availableModelCount());
        var stats = result.modelScores().get("a").indicators().get("CODE1");
        assertNotNull(stats);
        assertEquals(BigDecimal.valueOf(90.00).setScale(2), stats.meanScore());
        assertNotNull(stats.stdDev());
        assertTrue(stats.stdDev() > 0);
        assertEquals(3, stats.rawScores().length);
    }

    /** 5.4 规则基线 — mock ruleStrategy 返回固定分数 → deviationFromRule 正确 */
    @Test
    void deviationFromRuleBaselineIsCorrect() {
        var client = mockClient("model-a");
        clients.put("a", client);

        var strategy = mock(LlmScoringStrategy.class);
        when(factory.create(client)).thenReturn(strategy);
        when(strategy.scoreAll(any())).thenReturn(Map.of(
                "CODE1", score("CODE1", "指标1", 85, "llm"),
                "CODE2", score("CODE2", "指标2", 70, "llm")
        ));
        when(ruleStrategy.scoreAll(any())).thenReturn(Map.of(
                "CODE1", new LlmScoringStrategy.ScoreResult("CODE1", "指标1", BigDecimal.valueOf(80), "rule"),
                "CODE2", new LlmScoringStrategy.ScoreResult("CODE2", "指标2", BigDecimal.valueOf(75), "rule")
        ));

        var result = service.compare(new EvaluationContext(), 1);

        assertTrue(result.ruleBaseline().containsKey("CODE1"));
        assertEquals(80.0, result.ruleBaseline().get("CODE1"), 0.001);
        assertEquals(75.0, result.ruleBaseline().get("CODE2"), 0.001);

        var stats1 = result.modelScores().get("a").indicators().get("CODE1");
        assertNotNull(stats1.deviationFromRule());
        assertEquals(5.0, stats1.deviationFromRule(), 0.001); // 85 - 80 = 5

        var stats2 = result.modelScores().get("a").indicators().get("CODE2");
        assertNotNull(stats2.deviationFromRule());
        assertEquals(-5.0, stats2.deviationFromRule(), 0.001); // 70 - 75 = -5
    }

    /** 5.5 空列表 — clients 空 → totalModelCount=0, 不抛异常 */
    @Test
    void compareWithEmptyClientsReturnsEmpty() {
        var result = service.compare(new EvaluationContext(), 1);

        assertEquals(0, result.availableModelCount());
        assertEquals(0, result.totalModelCount());
        assertTrue(result.modelScores().isEmpty());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.ruleBaseline().isEmpty());
        assertTrue(result.crossModelVariance().isEmpty());
    }

    /** 5.6 单模型失败 — inject 2 clients, 第2个抛异常 → errors 含第2个, 第1个正常 */
    @Test
    void compareWithFailingClientCollectsError() {
        var clientA = mockClient("model-a");
        var clientB = mockClient("model-b");
        clients.put("a", clientA);
        clients.put("b", clientB);

        var stratA = mock(LlmScoringStrategy.class);
        when(factory.create(clientA)).thenReturn(stratA);
        when(factory.create(clientB)).thenThrow(new RuntimeException("API error"));
        when(stratA.scoreAll(any())).thenReturn(Map.of(
                "CODE1", score("CODE1", "指标1", 90, "ok")
        ));

        var result = service.compare(new EvaluationContext(), 1);

        assertEquals(1, result.availableModelCount());
        assertEquals(2, result.totalModelCount());
        assertTrue(result.modelScores().containsKey("a"));
        assertFalse(result.modelScores().containsKey("b"));
        assertTrue(result.errors().containsKey("b"));
        assertEquals(1, result.errors().size());
    }
}
