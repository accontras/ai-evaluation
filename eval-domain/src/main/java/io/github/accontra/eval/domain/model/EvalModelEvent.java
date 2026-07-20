package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("eval_model_event")
public class EvalModelEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelId;
    private Long sceneId;
    private String code;
    private String name;
    private String eventType;
    private String dimensionRule;
    private String scoreExpression;
    private Integer priority;
    private String redLineMessage;
    private String riskLevel;
    private String targetType;
    private Long targetId;
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
    public Long getSceneId() { return sceneId; }
    public void setSceneId(Long sceneId) { this.sceneId = sceneId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDimensionRule() { return dimensionRule; }
    public void setDimensionRule(String dimensionRule) { this.dimensionRule = dimensionRule; }
    public String getScoreExpression() { return scoreExpression; }
    public void setScoreExpression(String scoreExpression) { this.scoreExpression = scoreExpression; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public String getRedLineMessage() { return redLineMessage; }
    public void setRedLineMessage(String redLineMessage) { this.redLineMessage = redLineMessage; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
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
