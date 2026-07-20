package io.github.accontra.eval.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.accontra.eval.domain.model.EvalScene;
import io.github.accontra.eval.domain.service.EvalSceneService;
import io.github.accontra.eval.infrastructure.mapper.EvalSceneMapper;
import org.springframework.stereotype.Service;

@Service
public class EvalSceneServiceImpl implements EvalSceneService {
    private final EvalSceneMapper mapper;
    public EvalSceneServiceImpl(EvalSceneMapper mapper) { this.mapper = mapper; }

    @Override
    public EvalScene findByCode(String code) {
        return mapper.selectOne(new LambdaQueryWrapper<EvalScene>()
                .eq(EvalScene::getCode, code));
    }
}
