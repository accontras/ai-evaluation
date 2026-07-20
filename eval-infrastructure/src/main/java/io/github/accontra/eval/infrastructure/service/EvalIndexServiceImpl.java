package io.github.accontra.eval.infrastructure.service;

import io.github.accontra.eval.domain.model.EvalIndex;
import io.github.accontra.eval.domain.service.EvalIndexService;
import io.github.accontra.eval.infrastructure.mapper.EvalIndexMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EvalIndexServiceImpl implements EvalIndexService {
    private final EvalIndexMapper mapper;
    public EvalIndexServiceImpl(EvalIndexMapper mapper) { this.mapper = mapper; }

    @Override
    public Map<Long, EvalIndex> findMapByIds(List<Long> ids) {
        return mapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(EvalIndex::getId, Function.identity()));
    }
}
