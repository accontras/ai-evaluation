package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("eval_event_log")
public class EvalEventLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long objectLogId;

    private Long taskLogId;

    private String bizId;

    private String sceneCode;

    private String modelCode;

    private Integer sn;

    private String eventCode;

    private String eventName;

    private String eventType;

    private String dimensionRule;

    private BigDecimal scoreBefore;

    private BigDecimal scoreAfter;

    private BigDecimal eventScore;

    private String redLineMessage;

    private String triggerValues;

    private String isRedLine;
    private String triggerSource;

    private String status;

    @TableLogic
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getObjectLogId() {
        return objectLogId;
    }

    public void setObjectLogId(Long objectLogId) {
        this.objectLogId = objectLogId;
    }

    public Long getTaskLogId() {
        return taskLogId;
    }

    public void setTaskLogId(Long taskLogId) {
        this.taskLogId = taskLogId;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public Integer getSn() {
        return sn;
    }

    public void setSn(Integer sn) {
        this.sn = sn;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDimensionRule() {
        return dimensionRule;
    }

    public void setDimensionRule(String dimensionRule) {
        this.dimensionRule = dimensionRule;
    }

    public BigDecimal getScoreBefore() {
        return scoreBefore;
    }

    public void setScoreBefore(BigDecimal scoreBefore) {
        this.scoreBefore = scoreBefore;
    }

    public BigDecimal getScoreAfter() {
        return scoreAfter;
    }

    public void setScoreAfter(BigDecimal scoreAfter) {
        this.scoreAfter = scoreAfter;
    }

    public BigDecimal getEventScore() {
        return eventScore;
    }

    public void setEventScore(BigDecimal eventScore) {
        this.eventScore = eventScore;
    }

    public String getRedLineMessage() {
        return redLineMessage;
    }

    public void setRedLineMessage(String redLineMessage) {
        this.redLineMessage = redLineMessage;
    }

    public String getTriggerValues() {
        return triggerValues;
    }

    public void setTriggerValues(String triggerValues) {
        this.triggerValues = triggerValues;
    }

    public String getIsRedLine() {
        return isRedLine;
    }

    public void setIsRedLine(String isRedLine) {
        this.isRedLine = isRedLine;
    }
    public String getTriggerSource() { return triggerSource; }
    public void setTriggerSource(String v) { triggerSource = v; }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
