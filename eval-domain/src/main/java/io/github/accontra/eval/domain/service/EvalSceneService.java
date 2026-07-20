package io.github.accontra.eval.domain.service;

import io.github.accontra.eval.domain.model.EvalScene;

/** 单表 CRUD — 评估方案 */
public interface EvalSceneService {
    EvalScene findByCode(String code);
}
