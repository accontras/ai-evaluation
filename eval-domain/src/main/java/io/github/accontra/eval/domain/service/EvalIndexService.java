package io.github.accontra.eval.domain.service;

import io.github.accontra.eval.domain.model.EvalIndex;
import java.util.List;
import java.util.Map;

/** 单表 CRUD — 指标定义 */
public interface EvalIndexService {
    Map<Long, EvalIndex> findMapByIds(List<Long> ids);
}
