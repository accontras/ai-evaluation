package io.github.accontra.eval.domain.service;

import io.github.accontra.eval.domain.model.EvalModelStage;
import java.util.List;

/** 单表 CRUD — 模型维度树 */
public interface EvalModelStageService {
    List<EvalModelStage> findByModelId(Long modelId);
}
