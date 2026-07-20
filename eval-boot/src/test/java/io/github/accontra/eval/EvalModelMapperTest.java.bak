package io.github.accontra.eval;

import io.github.accontra.eval.domain.model.EvalModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EvalModelMapperTest {

    @Autowired
    private EvalModelMapper mapper;

    @Test
    void insertAndSelect() {
        EvalModel model = new EvalModel();
        model.setCode("TEST-001");
        model.setName("测试模型");
        model.setAggregateMode("weighted_sum");
        model.setStatus("DRAFT");
        model.setEnabled(1);

        int rows = mapper.insert(model);
        assertThat(rows).isEqualTo(1);
        assertThat(model.getId()).isNotNull();

        EvalModel found = mapper.selectById(model.getId());
        assertThat(found).isNotNull();
        assertThat(found.getCode()).isEqualTo("TEST-001");
        assertThat(found.getName()).isEqualTo("测试模型");
    }
}
