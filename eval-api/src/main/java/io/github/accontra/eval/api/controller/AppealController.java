package io.github.accontra.eval.api.controller;

import io.github.accontra.eval.common.Result;
import io.github.accontra.eval.domain.model.EvalAppealHeader;
import io.github.accontra.eval.infrastructure.mapper.EvalAppealDetailMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalAppealHeaderMapper;
import io.github.accontra.eval.infrastructure.mapper.EvalObjectLogMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 申诉 Controller — S22。
 *
 * 流程: 提交申诉(PENDING) → 审批(APPROVED) → 触发重算
 */
@RestController
@RequestMapping("/api/v1/appeal")
public class AppealController {

    private final EvalAppealHeaderMapper appealMapper;
    private final EvalAppealDetailMapper detailMapper;
    private final EvalObjectLogMapper objectLogMapper;

    public AppealController(EvalAppealHeaderMapper appealMapper,
                             EvalAppealDetailMapper detailMapper,
                             EvalObjectLogMapper objectLogMapper) {
        this.appealMapper = appealMapper;
        this.detailMapper = detailMapper;
        this.objectLogMapper = objectLogMapper;
    }

    /** 提交申诉 */
    @PostMapping("/submit")
    public Result<EvalAppealHeader> submit(@RequestBody Map<String, Object> body) {
        var appeal = new EvalAppealHeader();
        appeal.setAppealNo("APL-" + System.currentTimeMillis());
        appeal.setAppealType((String) body.get("appealType"));
        appeal.setSceneId(Long.valueOf(body.get("sceneId").toString()));
        appeal.setObjectId(Long.valueOf(body.get("objectId").toString()));
        appeal.setScoreAdjustment(body.get("scoreAdjustment") != null
                ? new BigDecimal(body.get("scoreAdjustment").toString()) : BigDecimal.ZERO);
        appeal.setReason((String) body.get("reason"));
        appeal.setStatus("PENDING");
        appeal.setSubmitTime(LocalDateTime.now());
        appeal.setEnabled(1);
        appeal.setCreateTime(LocalDateTime.now());
        appeal.setUpdateTime(LocalDateTime.now());
        appealMapper.insert(appeal);
        return Result.ok(appeal);
    }

    /** 审批申诉 (APPROVED → 立即执行重算) */
    @PostMapping("/{id}/approve")
    public Result<Map<String, Object>> approve(@PathVariable("id") Long id) {
        var appeal = appealMapper.selectById(id);
        if (appeal == null) return Result.fail("B0001", "申诉不存在");
        if (!"PENDING".equals(appeal.getStatus())) return Result.fail("B0001", "只能审批 PENDING 状态");

        var objLog = objectLogMapper.selectById(appeal.getObjectId());
        if (objLog == null) return Result.fail("B0001", "评估对象不存在");

        BigDecimal originalScore = objLog.getTotalScore() != null ? objLog.getTotalScore() : BigDecimal.ZERO;
        BigDecimal adjustment = appeal.getScoreAdjustment() != null ? appeal.getScoreAdjustment() : BigDecimal.ZERO;
        BigDecimal adjusted = originalScore.add(adjustment);

        // 防止超出 0-100
        if (adjusted.compareTo(BigDecimal.ZERO) < 0) adjusted = BigDecimal.ZERO;
        if (adjusted.compareTo(BigDecimal.valueOf(100)) > 0) adjusted = BigDecimal.valueOf(100);

        // 更新对象日志
        objLog.setAppealAdjustedScore(adjusted);
        objLog.setUpdateTime(LocalDateTime.now());
        objectLogMapper.updateById(objLog);

        // 更新申诉
        appeal.setAdjustedTotalScore(adjusted);
        appeal.setStatus("APPROVED");
        appeal.setExecuteTime(LocalDateTime.now());
        appeal.setUpdateTime(LocalDateTime.now());
        appealMapper.updateById(appeal);

        return Result.ok(Map.of(
                "appealId", id,
                "appealType", appeal.getAppealType(),
                "originalScore", originalScore,
                "adjustment", adjustment,
                "adjustedScore", adjusted,
                "status", "APPROVED"
        ));
    }

    /** 申诉列表 */
    @GetMapping("/list")
    public Result<Object> list(@RequestParam(value = "sceneId", required = false) Long sceneId) {
        var qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvalAppealHeader>()
                .orderByDesc(EvalAppealHeader::getCreateTime);
        if (sceneId != null) {
            qw.eq(EvalAppealHeader::getSceneId, sceneId);
        }
        return Result.ok(appealMapper.selectList(qw));
    }
}
