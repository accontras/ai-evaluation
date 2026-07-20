package io.github.accontra.eval.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.accontra.eval.domain.model.EvalTarget;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EvalTargetMapper extends BaseMapper<EvalTarget> {
}
