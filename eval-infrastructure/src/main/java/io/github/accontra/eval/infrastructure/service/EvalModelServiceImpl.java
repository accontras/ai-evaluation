package io.github.accontra.eval.infrastructure.service;

import io.github.accontra.eval.domain.model.EvalModel;
import io.github.accontra.eval.domain.service.EvalModelService;
import io.github.accontra.eval.infrastructure.mapper.EvalModelMapper;
import org.springframework.stereotype.Service;

@Service
public class EvalModelServiceImpl implements EvalModelService {
    private final EvalModelMapper mapper;
    public EvalModelServiceImpl(EvalModelMapper mapper) { this.mapper = mapper; }

    @Override
    public EvalModel findById(Long id) {
        return mapper.selectById(id);
    }
}
