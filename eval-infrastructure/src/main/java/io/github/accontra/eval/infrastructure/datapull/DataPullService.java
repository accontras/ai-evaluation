package io.github.accontra.eval.infrastructure.datapull;

import io.github.accontra.eval.domain.model.RawData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据取数服务 — 在 Pipeline 之前执行，从数据源拉取原始指标数据。
 * 三路径: A(请求直传) / B(viewCode分组取数, 主力) / C(SQL兜底)
 *
 * S7 实现路径A，路径B/C 在 S20 补充。
 */
public class DataPullService {

    private static final Logger log = LoggerFactory.getLogger(DataPullService.class);

    /**
     * 路径A: 请求直传数据 — 直接使用调用方传入的 data。
     * data 格式: Map<bizId, Map<fieldCode, value>>
     */
    public Map<String, RawData> pullDirect(Map<String, Map<String, Object>> requestData, List<String> bizIds) {
        Map<String, RawData> result = new LinkedHashMap<>();

        for (String bizId : bizIds) {
            RawData raw = new RawData(bizId);
            if (requestData != null && requestData.containsKey(bizId)) {
                raw.setFields(new LinkedHashMap<>(requestData.get(bizId)));
            }
            result.put(bizId, raw);
        }

        log.info("[DataPull] 路径A: {} 个对象", result.size());
        return result;
    }
}
