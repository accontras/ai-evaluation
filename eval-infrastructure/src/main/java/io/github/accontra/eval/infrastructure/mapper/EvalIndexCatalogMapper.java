package io.github.accontra.eval.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.accontra.eval.domain.model.EvalIndexCatalog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EvalIndexCatalogMapper extends BaseMapper<EvalIndexCatalog> {
}
