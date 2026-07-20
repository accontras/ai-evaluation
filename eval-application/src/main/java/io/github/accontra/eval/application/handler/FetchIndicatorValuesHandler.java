package io.github.accontra.eval.application.handler;

import io.github.accontra.eval.application.pipeline.EvaluationContext;
import io.github.accontra.eval.common.enums.ErrorCode;
import io.github.accontra.eval.common.exception.EvalException;
import io.github.accontra.eval.domain.model.EvalDimension;
import io.github.accontra.eval.domain.model.EvalIndex;
import io.github.accontra.eval.domain.model.EvalModelIndex;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * H2 — 提取指标值 (纯内存操作，零 IO)。
 *
 * 职责: 从 Controller 预拉取的 rawData 中，通过 dimensions 声明机制
 * 提取指标原始值和维度属性值。
 *
 * 核心算法:
 *   1. rawData.fields[fieldCode] → rawValues (dimCodes[0] 主属性)
 *   2. rawData.fields[fieldCode] → attrValues (dimCodes[0..N] 全部维度)
 *   3. supplementAttrValuesFromDimDefinitions 补齐未映射的维度
 */
public class FetchIndicatorValuesHandler implements Handler {

    private static final Logger log = LoggerFactory.getLogger(FetchIndicatorValuesHandler.class);

    @Override public String stepCode() { return "FETCH"; }
    @Override public String stepName() { return "获取指标值"; }
    @Override public int order() { return 2; }

    @Override
    public void execute(EvaluationContext ctx) {
        // 1. 校验入参 — S7 路径A: rawData 是 Map<fieldCode, value>
        Map<String, Object> fields = ctx.getRawData();
        if (fields == null || fields.isEmpty()) {
            throw new EvalException(ErrorCode.RAW_DATA_EMPTY.code(), ErrorCode.RAW_DATA_EMPTY.message());
        }

        // 2. 提取
        List<EvalModelIndex> modelIndices = ctx.getModelIndices();
        Map<String, EvalIndex> indexBaseMap = ctx.getIndexBaseMap();
        Map<String, EvalDimension> dimDefinitions = ctx.getDimDefinitions();

        Map<String, Object> rawValues = new LinkedHashMap<>();
        Map<String, Object> attrValues = new LinkedHashMap<>();
        int successCount = 0;
        int failedCount = 0;

        for (EvalModelIndex mi : modelIndices) {
            EvalIndex indexBase = indexBaseMap != null ? indexBaseMap.get(String.valueOf(mi.getIndexId())) : null;
            if (indexBase == null) {
                log.warn("[H2] 指标定义缺失, bizId={}, indexId={}", ctx.getBizId(), mi.getIndexId());
                failedCount++;
                continue;
            }

            String indexCode = indexBase.getCode();
            String dimensionsStr = indexBase.getDimensions();
            if (dimensionsStr == null || dimensionsStr.isBlank()) {
                log.warn("[H2] 指标未声明维度, bizId={}, indexCode={}", ctx.getBizId(), indexCode);
                failedCount++;
                continue;
            }

            String[] dimCodes = parseDimCodes(dimensionsStr);
            if (dimCodes.length == 0) {
                log.warn("[H2] 指标维度列表为空, bizId={}, indexCode={}", ctx.getBizId(), indexCode);
                failedCount++;
                continue;
            }

            // 指标原始值: dimCodes[0] 主属性 → fieldCode → fields
            String mainDimCode = dimCodes[0];
            EvalDimension mainDimDef = getDimDef(dimDefinitions, mainDimCode);
            if (mainDimDef == null || mainDimDef.getFieldCode() == null) {
                log.warn("[H2] 主属性维度未注册, bizId={}, indexCode={}, dimCode={}",
                        ctx.getBizId(), indexCode, mainDimCode);
                failedCount++;
                continue;
            }

            Object value = fields.get(mainDimDef.getFieldCode());
            rawValues.put(indexCode, value);
            if (value != null) successCount++;
            else failedCount++;

            // 属性值: dimCodes 全部维度 → fieldCode → fields
            for (String dimCode : dimCodes) {
                EvalDimension dimDef = getDimDef(dimDefinitions, dimCode);
                if (dimDef == null) {
                    log.warn("[H2] 维度未注册, bizId={}, dimCode={}", ctx.getBizId(), dimCode);
                    continue;
                }
                if (dimDef.getFieldCode() == null || dimDef.getFieldCode().isBlank()) {
                    continue;
                }

                Object attrValue = fields.get(dimDef.getFieldCode());
                String dimName = dimDef.getName() != null ? dimDef.getName() : dimCode;

                if (attrValues.containsKey(dimName)
                        && !Objects.equals(attrValues.get(dimName), attrValue)) {
                    log.warn("[H2] 维度值覆盖, bizId={}, dimName={}, old={}, new={}",
                            ctx.getBizId(), dimName, attrValues.get(dimName), attrValue);
                }
                attrValues.put(dimName, attrValue);
            }
        }

        // 3. 补齐维度属性 (ADR-019)
        supplementAttrValuesFromDimDefinitions(attrValues, dimDefinitions, fields);

        // 4. 写入 Context
        ctx.setRawValues(rawValues);

        if (successCount == 0 && modelIndices != null && !modelIndices.isEmpty()) {
            throw new EvalException(ErrorCode.ALL_INDICATORS_FAILED.code(),
                    ErrorCode.ALL_INDICATORS_FAILED.message());
        }

        ctx.setAttrValues(attrValues);

        log.info("[H2] 完成, bizId={}, total={}, success={}, failed={}, attrCount={}",
                ctx.getBizId(), modelIndices != null ? modelIndices.size() : 0,
                successCount, failedCount, attrValues.size());
    }

    // ---- helper methods ----

    private String[] parseDimCodes(String dimensionsStr) {
        try {
            List<String> list = JSONUtil.toList(dimensionsStr, String.class);
            return list.stream().filter(s -> s != null && !s.isBlank()).toArray(String[]::new);
        } catch (Exception e) {
            log.warn("[H2] dimensions JSON 解析失败: {}", dimensionsStr, e);
            return new String[0];
        }
    }

    private EvalDimension getDimDef(Map<String, EvalDimension> dimDefinitions, String dimCode) {
        if (dimDefinitions == null || dimCode == null) return null;
        return dimDefinitions.get(dimCode);
    }

    /**
     * ADR-019: 将 dimDefinitions 中尚未出现在 attrValues 里的维度，
     * 通过 fieldCode 从 fields 中补齐。确保事件表达式有完整变量上下文。
     */
    private void supplementAttrValuesFromDimDefinitions(
            Map<String, Object> attrValues,
            Map<String, EvalDimension> dimDefinitions,
            Map<String, Object> fields) {

        if (dimDefinitions == null || fields == null) return;

        for (EvalDimension dimDef : dimDefinitions.values()) {
            String dimName = dimDef.getName();
            if (dimName == null || attrValues.containsKey(dimName)) continue;

            String fieldCode = dimDef.getFieldCode();
            if (fieldCode != null && fields.containsKey(fieldCode)) {
                attrValues.put(dimName, fields.get(fieldCode));
            }
        }
    }
}
