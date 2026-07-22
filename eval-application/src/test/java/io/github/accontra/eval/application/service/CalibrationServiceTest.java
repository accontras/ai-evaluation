package io.github.accontra.eval.application.service;

import io.github.accontra.eval.domain.model.EvalIndicatorLog;
import io.github.accontra.eval.infrastructure.mapper.EvalIndicatorLogMapper;
import io.github.accontra.eval.infrastructure.rag.EmbeddingService;
import io.github.accontra.eval.infrastructure.rag.QdrantVectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CalibrationServiceTest {

    private EvalIndicatorLogMapper indicatorLogMapper;
    private EmbeddingService embeddingService;
    private QdrantVectorService qdrantVectorService;
    private CalibrationService calibrationService;

    @BeforeEach
    void setUp() {
        indicatorLogMapper = mock(EvalIndicatorLogMapper.class);
        embeddingService = mock(EmbeddingService.class);
        qdrantVectorService = mock(QdrantVectorService.class);
        calibrationService = new CalibrationService(indicatorLogMapper, embeddingService, qdrantVectorService);
    }

    @Test
    void calibrateSuccess() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(qdrantVectorService.isAvailable()).thenReturn(true);
        var log = newLog(1L, "good");
        when(indicatorLogMapper.selectById(1L)).thenReturn(log);
        when(embeddingService.encode(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        var result = calibrationService.calibrate(List.of(1L));

        assertEquals(1, result.total());
        assertEquals(1, result.success());
        assertEquals(0, result.failed());
        assertEquals("SUCCESS", result.details().get(0).status());
        verify(qdrantVectorService).upsert(anyList());
    }

    @Test
    void calibrateRejectsEmptyLlmReason() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(qdrantVectorService.isAvailable()).thenReturn(true);
        var log = newLog(1L, null);
        log.setLlmReason(null);
        when(indicatorLogMapper.selectById(1L)).thenReturn(log);

        var result = calibrationService.calibrate(List.of(1L));

        assertEquals(1, result.total());
        assertEquals(0, result.success());
        assertEquals(1, result.failed());
        assertEquals("FAILED", result.details().get(0).status());
        assertTrue(result.details().get(0).error().contains("LLM"),
                () -> "Expected LLM-related error, got: " + result.details().get(0).error());
        verify(qdrantVectorService, never()).upsert(anyList());
    }

    @Test
    void calibrateRejectsBlankLlmReason() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(qdrantVectorService.isAvailable()).thenReturn(true);
        var log = newLog(1L, "   ");
        when(indicatorLogMapper.selectById(1L)).thenReturn(log);

        var result = calibrationService.calibrate(List.of(1L));

        assertEquals(0, result.success());
        assertEquals(1, result.failed());
        verify(qdrantVectorService, never()).upsert(anyList());
    }

    @Test
    void calibrateRecordNotFound() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(qdrantVectorService.isAvailable()).thenReturn(true);
        when(indicatorLogMapper.selectById(999L)).thenReturn(null);

        var result = calibrationService.calibrate(List.of(999L));

        assertEquals(1, result.total());
        assertEquals(0, result.success());
        assertEquals(1, result.failed());
        assertTrue(result.details().get(0).error().contains("记录不存在"),
                () -> "Expected 'record not found' error, got: " + result.details().get(0).error());
    }

    @Test
    void calibrateBatchMixed() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(qdrantVectorService.isAvailable()).thenReturn(true);
        when(embeddingService.encode(anyString())).thenReturn(new float[]{0.1f});

        when(indicatorLogMapper.selectById(1L)).thenReturn(newLog(1L, "good reason"));
        when(indicatorLogMapper.selectById(2L)).thenReturn(null);
        var log3 = newLog(3L, null);
        log3.setLlmReason(null);
        when(indicatorLogMapper.selectById(3L)).thenReturn(log3);

        var result = calibrationService.calibrate(List.of(1L, 2L, 3L));

        assertEquals(3, result.total());
        assertEquals(1, result.success());
        assertEquals(2, result.failed());
        assertEquals("SUCCESS", result.details().get(0).status());
        assertEquals("FAILED", result.details().get(1).status());
        assertEquals("FAILED", result.details().get(2).status());
    }

    @Test
    void calibrateEmbeddingUnavailable() {
        when(embeddingService.isAvailable()).thenReturn(false);

        var result = calibrationService.calibrate(List.of(1L, 2L));

        assertEquals(2, result.total());
        assertEquals(0, result.success());
        assertEquals(2, result.failed());
        assertTrue(result.details().get(0).error().contains("Embedding"));
        verifyNoInteractions(indicatorLogMapper);
    }

    @Test
    void calibrateQdrantUnavailable() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(qdrantVectorService.isAvailable()).thenReturn(false);

        var result = calibrationService.calibrate(List.of(1L));

        assertEquals(1, result.total());
        assertEquals(0, result.success());
        assertEquals(1, result.failed());
        assertTrue(result.details().get(0).error().contains("Qdrant"));
        verifyNoInteractions(indicatorLogMapper);
    }

    @Test
    void calibrateNullIds() {
        var result = calibrationService.calibrate(null);
        assertEquals(0, result.total());
        assertEquals(0, result.success());
        assertEquals(0, result.failed());
        verifyNoInteractions(indicatorLogMapper);
    }

    @Test
    void calibrateEmptyIds() {
        var result = calibrationService.calibrate(Collections.emptyList());
        assertEquals(0, result.total());
        assertEquals(0, result.success());
        assertEquals(0, result.failed());
        verifyNoInteractions(indicatorLogMapper);
    }

    @Test
    void calibrateIdempotent() {
        when(embeddingService.isAvailable()).thenReturn(true);
        when(qdrantVectorService.isAvailable()).thenReturn(true);
        when(embeddingService.encode(anyString())).thenReturn(new float[]{0.1f});
        var log = newLog(1L, "consistent reason");
        when(indicatorLogMapper.selectById(1L)).thenReturn(log);

        var r1 = calibrationService.calibrate(List.of(1L));
        assertEquals(1, r1.success());

        var r2 = calibrationService.calibrate(List.of(1L));
        assertEquals(1, r2.success());
        verify(qdrantVectorService, times(2)).upsert(anyList());
    }

    private static EvalIndicatorLog newLog(Long id, String llmReason) {
        var log = new EvalIndicatorLog();
        log.setId(id);
        log.setIndexCode("COST_001");
        log.setIndexName("cost metric");
        log.setDataValue("123.45");
        log.setLlmScore(BigDecimal.valueOf(80));
        log.setLlmReason(llmReason);
        log.setDiffLevel("TRIVIAL");
        return log;
    }
}
