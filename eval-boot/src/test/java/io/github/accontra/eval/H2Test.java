package io.github.accontra.eval;

import io.github.accontra.eval.application.handler.FetchIndicatorValuesHandler;
import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.domain.model.EvalDimension;
import io.github.accontra.eval.domain.model.EvalIndex;
import io.github.accontra.eval.domain.model.EvalModelIndex;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class H2Test {

    @Test
    void shouldExtractRawValueAndAttrValues() {
        // given: rawData 直传 (路径A)
        var ctx = new EvaluationContext();
        ctx.setBizId("E001");
        ctx.setRawData(Map.of(
                "log_fill_rate", 85.5,
                "entry_days", 73,
                "org_name", "华东区"
        ));

        // dimDefinitions
        var logFillDim = new EvalDimension();
        logFillDim.setCode("LOG_FILL");
        logFillDim.setName("日志填报率");
        logFillDim.setFieldCode("log_fill_rate");

        var entryDim = new EvalDimension();
        entryDim.setCode("ENTRY_DAYS");
        entryDim.setName("入职天数");
        entryDim.setFieldCode("entry_days");

        ctx.setDimDefinitions(Map.of("LOG_FILL", logFillDim, "ENTRY_DAYS", entryDim));

        // indexBase
        var indexBase = new EvalIndex();
        indexBase.setId(1L);
        indexBase.setCode("LOG_FILL_RATE");
        indexBase.setDimensions("[\"LOG_FILL\", \"ENTRY_DAYS\"]");
        ctx.setIndexBaseMap(Map.of("1", indexBase));

        // modelIndex (挂到 stage)
        var mi = new EvalModelIndex();
        mi.setIndexId(1L);
        ctx.setModelIndices(List.of(mi));

        // when
        var handler = new FetchIndicatorValuesHandler();
        handler.execute(ctx);

        // then
        assertThat(ctx.getRawValues()).containsKey("LOG_FILL_RATE");
        assertThat(ctx.getRawValues().get("LOG_FILL_RATE")).isEqualTo(85.5);
        assertThat(ctx.getAttrValues()).containsKeys("日志填报率", "入职天数");
    }

    @Test
    void shouldThrowWhenRawDataEmpty() {
        var ctx = new EvaluationContext();
        ctx.setModelIndices(List.of(new EvalModelIndex()));

        var handler = new FetchIndicatorValuesHandler();
        assertThatThrownBy(() -> handler.execute(ctx))
                .hasMessageContaining("评估数据为空");
    }

    @Test
    void shouldSkipIndicatorWithoutDimensions() {
        var ctx = new EvaluationContext();
        ctx.setBizId("E001");
        ctx.setRawData(Map.of("x", 1));
        ctx.setDimDefinitions(Map.of());

        var indexBase = new EvalIndex();
        indexBase.setId(1L);
        indexBase.setCode("NO_DIM_INDEX");
        indexBase.setDimensions("[]"); // 空数组
        ctx.setIndexBaseMap(Map.of("1", indexBase));

        var mi = new EvalModelIndex();
        mi.setIndexId(1L);
        ctx.setModelIndices(List.of(mi));

        var handler = new FetchIndicatorValuesHandler();
        // 不应抛异常，但有 failedCount
        assertThatThrownBy(() -> handler.execute(ctx))
                .hasMessageContaining("全部指标取值失败");
    }
}
