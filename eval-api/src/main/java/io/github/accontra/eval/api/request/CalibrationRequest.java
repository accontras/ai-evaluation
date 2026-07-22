package io.github.accontra.eval.api.request;

import java.util.List;

/**
 * 标定请求 — 提交待向量化入库的 indicator_log ID 列表。
 */
public class CalibrationRequest {
    private List<Long> ids;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
}
