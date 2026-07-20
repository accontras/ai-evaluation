package io.github.accontra.eval.domain.service;

import io.github.accontra.eval.domain.model.EvalModelIndex;
import java.util.List;

/** 单表 CRUD — 模型指标关联 */
public interface EvalModelIndexService {
    List<EvalModelIndex> findByModelId(Long modelId);
}
