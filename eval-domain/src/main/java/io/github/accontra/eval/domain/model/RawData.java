package io.github.accontra.eval.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 取数结果 — 一个业务对象的原始指标数据。
 * key = fieldCode (dimensionOptions 重写后的框架内部字段名)
 */
public class RawData {
    private String bizId;
    private Map<String, Object> fields = new LinkedHashMap<>();

    public RawData() {}

    public RawData(String bizId) {
        this.bizId = bizId;
    }

    public String getBizId() { return bizId; }
    public void setBizId(String v) { bizId = v; }
    public Map<String, Object> getFields() { return fields; }
    public void setFields(Map<String, Object> v) { fields = v; }
}
