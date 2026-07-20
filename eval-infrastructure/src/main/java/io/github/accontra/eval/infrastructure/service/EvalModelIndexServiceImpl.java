package io.github.accontra.eval.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.accontra.eval.domain.model.EvalModelIndex;
import io.github.accontra.eval.domain.service.EvalModelIndexService;
import io.github.accontra.eval.infrastructure.mapper.EvalModelIndexMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EvalModelIndexServiceImpl implements EvalModelIndexService {
    private final EvalModelIndexMapper mapper;
    public EvalModelIndexServiceImpl(EvalModelIndexMapper mapper) { this.mapper = mapper; }

    @Override
    public List<EvalModelIndex> findByModelId(Long modelId) {
        return mapper.selectList(new LambdaQueryWrapper<EvalModelIndex>()
                .eq(EvalModelIndex::getModelId, modelId)
                .orderByAsc(EvalModelIndex::getSn));
    }
}
