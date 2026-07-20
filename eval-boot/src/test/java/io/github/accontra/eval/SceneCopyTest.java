package io.github.accontra.eval;

import io.github.accontra.eval.application.service.SceneCopyDomainService;
import io.github.accontra.eval.infrastructure.mapper.EvalSceneMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalSceneStageMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalSceneIndexMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S28: 深拷贝集成测试
 */
@SpringBootTest
class SceneCopyTest {

    @Autowired private SceneCopyDomainService copyService;
    @Autowired private EvalSceneMapper sceneMapper;
    @Autowired private EvalSceneStageMapper stageMapper;
    @Autowired private EvalSceneIndexMapper indexMapper;

    @Test
    void shouldDeepCopyModelToScene() {
        String sceneCode = "TEST-COPY-" + System.currentTimeMillis();
        var scene = copyService.copyFromModel(1L, sceneCode, "Test Copy");

        assertThat(scene.getId()).isNotNull();
        assertThat(scene.getCode()).isEqualTo(sceneCode);
        assertThat(scene.getStatus()).isEqualTo("DRAFT");
        assertThat(scene.getModelId()).isEqualTo(1L);

        // Verify stages copied
        var stages = stageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        io.github.accontra.eval.domain.model.EvalSceneStage>()
                        .eq(io.github.accontra.eval.domain.model.EvalSceneStage::getSceneId, scene.getId()));
        assertThat(stages).isNotEmpty();
        // At least 3 stages (from model tree)
        assertThat(stages.size()).isGreaterThanOrEqualTo(3);

        // Verify indices copied
        var indices = indexMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        io.github.accontra.eval.domain.model.EvalSceneIndex>()
                        .eq(io.github.accontra.eval.domain.model.EvalSceneIndex::getSceneId, scene.getId()));
        assertThat(indices).isNotEmpty();
        assertThat(indices.size()).isEqualTo(3); // 3 model indices

        // Verify stageId remap
        for (var idx : indices) {
            assertThat(idx.getStageId()).isNotNull();
            assertThat(idx.getIndexCode()).isNotNull();
            assertThat(idx.getIndexName()).isNotNull();
        }

        // Verify parentId remap
        var stageMap = stages.stream()
                .collect(java.util.stream.Collectors.toMap(
                        io.github.accontra.eval.domain.model.EvalSceneStage::getId, s -> s));
        for (var s : stages) {
            if (s.getParentId() != null && s.getParentId() > 0) {
                assertThat(stageMap).containsKey(s.getParentId()); // parent exists
            }
        }

        System.out.printf("✅ Deep copy OK: scene=%s, stages=%d, indices=%d%n",
                sceneCode, stages.size(), indices.size());
    }

    @Test
    void shouldPublishScene() {
        String sceneCode = "TEST-PUB-" + System.currentTimeMillis();
        var scene = copyService.copyFromModel(1L, sceneCode, "Publish Test");
        assertThat(scene.getStatus()).isEqualTo("DRAFT");

        copyService.publish(scene.getId());

        var published = sceneMapper.selectById(scene.getId());
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
    }
}
