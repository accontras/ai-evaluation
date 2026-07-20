package io.github.accontra.eval.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.accontra.eval.common.Result;
import io.github.accontra.eval.domain.model.EvalGradeMapping;
import io.github.accontra.eval.infrastructure.mapper.EvalGradeMappingMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 等级映射 CRUD — SCORE_RANGE 模式。
 */
@RestController
@RequestMapping("/api/v1/grade-mapping")
public class GradeMappingController {

    private final EvalGradeMappingMapper mapper;

    public GradeMappingController(EvalGradeMappingMapper mapper) { this.mapper = mapper; }

    /** 查询方案下所有等级配置 */
    @GetMapping("/list")
    public Result<List<EvalGradeMapping>> list(@RequestParam("sceneId") Long sceneId) {
        var qw = new LambdaQueryWrapper<EvalGradeMapping>()
                .eq(EvalGradeMapping::getSceneId, sceneId)
                .orderByAsc(EvalGradeMapping::getPriority);
        return Result.ok(mapper.selectList(qw));
    }

    /** 新增单条 */
    @PostMapping
    public Result<EvalGradeMapping> create(@RequestBody EvalGradeMapping gm) {
        gm.setCreateTime(LocalDateTime.now());
        gm.setUpdateTime(LocalDateTime.now());
        gm.setEnabled(1);
        mapper.insert(gm);
        return Result.ok(gm);
    }

    /** 批量保存 (先删后插) */
    @PostMapping("/batch")
    public Result<Map<String, Object>> batchSave(@RequestParam("sceneId") Long sceneId,
                                                  @RequestBody List<EvalGradeMapping> mappings) {
        var delQw = new LambdaQueryWrapper<EvalGradeMapping>()
                .eq(EvalGradeMapping::getSceneId, sceneId);
        mapper.delete(delQw);

        int sn = 1;
        for (var gm : mappings) {
            gm.setSceneId(sceneId);
            gm.setPriority(sn++);
            gm.setCreateTime(LocalDateTime.now());
            gm.setUpdateTime(LocalDateTime.now());
            gm.setEnabled(1);
            mapper.insert(gm);
        }
        return Result.ok(Map.of("saved", mappings.size()));
    }

    /** 根据分数查等级 */
    @GetMapping("/grade")
    public Result<String> grade(@RequestParam("sceneId") Long sceneId,
                                 @RequestParam("score") BigDecimal score) {
        var qw = new LambdaQueryWrapper<EvalGradeMapping>()
                .eq(EvalGradeMapping::getSceneId, sceneId)
                .le(EvalGradeMapping::getLowerBound, score)
                .gt(EvalGradeMapping::getUpperBound, score)
                .orderByAsc(EvalGradeMapping::getPriority)
                .last("LIMIT 1");
        var gm = mapper.selectOne(qw);
        return Result.ok(gm != null ? gm.getGrade() : "N/A");
    }
}
