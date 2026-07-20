package io.github.accontra.eval.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.accontra.eval.domain.model.EvalModelStage;
import io.github.accontra.eval.domain.service.EvalModelStageService;
import io.github.accontra.eval.infrastructure.mapper.EvalModelStageMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EvalModelStageServiceImpl implements EvalModelStageService {
    private final EvalModelStageMapper mapper;
    public EvalModelStageServiceImpl(EvalModelStageMapper mapper) { this.mapper = mapper; }

    @Override
    public List<EvalModelStage> findByModelId(Long modelId) {
        return mapper.selectList(new LambdaQueryWrapper<EvalModelStage>()
                .eq(EvalModelStage::getModelId, modelId)
                .orderByAsc(EvalModelStage::getSn));
    }
}
