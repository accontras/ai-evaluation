package io.github.accontra.eval.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.accontra.eval.domain.model.EvalIndex;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EvalIndexMapper extends BaseMapper<EvalIndex> {
}
