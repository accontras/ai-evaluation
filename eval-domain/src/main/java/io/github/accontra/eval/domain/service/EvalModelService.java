package io.github.accontra.eval.domain.service;

import io.github.accontra.eval.domain.model.EvalModel;

/** 单表 CRUD — 评估模型 */
public interface EvalModelService {
    EvalModel findById(Long id);
}
