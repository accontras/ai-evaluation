package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("eval_model_standard")
public class EvalModelStandard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelId;
    private Long stageId;
    private Long indexId;
    private Long modelIndexId;
    private Long sceneId;
    private String targetType;
    private Long targetId;
    private String code;
    private String standardType;
    private String dimensionRule;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String dictValue;
    private BigDecimal score;
    private String scoreMode;
    private String scoreType;
    private String scoreExpression;
    private Integer priority;
    private Long ruleId;
    @TableLogic
    private Integer enabled;
    private String memo;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public Long getStageId() { return stageId; }
    public void setStageId(Long stageId) { this.stageId = stageId; }
    public Long getIndexId() { return indexId; }
    public void setIndexId(Long indexId) { this.indexId = indexId; }
    public Long getModelIndexId() { return modelIndexId; }
    public void setModelIndexId(Long modelIndexId) { this.modelIndexId = modelIndexId; }
    public Long getSceneId() { return sceneId; }
    public void setSceneId(Long sceneId) { this.sceneId = sceneId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getStandardType() { return standardType; }
    public void setStandardType(String standardType) { this.standardType = standardType; }
    public String getDimensionRule() { return dimensionRule; }
    public void setDimensionRule(String dimensionRule) { this.dimensionRule = dimensionRule; }
    public BigDecimal getMinValue() { return minValue; }
    public void setMinValue(BigDecimal minValue) { this.minValue = minValue; }
    public BigDecimal getMaxValue() { return maxValue; }
    public void setMaxValue(BigDecimal maxValue) { this.maxValue = maxValue; }
    public String getDictValue() { return dictValue; }
    public void setDictValue(String dictValue) { this.dictValue = dictValue; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getScoreMode() { return scoreMode; }
    public void setScoreMode(String scoreMode) { this.scoreMode = scoreMode; }
    public String getScoreType() { return scoreType; }
    public void setScoreType(String scoreType) { this.scoreType = scoreType; }
    public String getScoreExpression() { return scoreExpression; }
    public void setScoreExpression(String scoreExpression) { this.scoreExpression = scoreExpression; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
