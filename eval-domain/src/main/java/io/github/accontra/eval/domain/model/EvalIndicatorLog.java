package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("eval_indicator_log")
public class EvalIndicatorLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long objectLogId;

    private Long taskLogId;

    private String clazz;

    private String stageCode;

    private String indexCode;

    private String indexName;

    private Integer sn;

    private Integer weight;

    private BigDecimal intervalWeight;

    private String dataValue;

    private BigDecimal score;

    private BigDecimal standardScore;

    private BigDecimal stageScore;

    private String scoreMode;

    private String dimensionRule;

    private String isRedLine;

    private String riskLevel;

    private Integer priority;

    private String evaluateInstance;

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

    public String getClazz() {
        return clazz;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public String getStageCode() {
        return stageCode;
    }

    public void setStageCode(String stageCode) {
        this.stageCode = stageCode;
    }

    public String getIndexCode() {
        return indexCode;
    }

    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public Integer getSn() {
        return sn;
    }

    public void setSn(Integer sn) {
        this.sn = sn;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public BigDecimal getIntervalWeight() {
        return intervalWeight;
    }

    public void setIntervalWeight(BigDecimal intervalWeight) {
        this.intervalWeight = intervalWeight;
    }

    public String getDataValue() {
        return dataValue;
    }

    public void setDataValue(String dataValue) {
        this.dataValue = dataValue;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public BigDecimal getStandardScore() {
        return standardScore;
    }

    public void setStandardScore(BigDecimal standardScore) {
        this.standardScore = standardScore;
    }

    public BigDecimal getStageScore() {
        return stageScore;
    }

    public void setStageScore(BigDecimal stageScore) {
        this.stageScore = stageScore;
    }

    public String getScoreMode() {
        return scoreMode;
    }

    public void setScoreMode(String scoreMode) {
        this.scoreMode = scoreMode;
    }

    public String getDimensionRule() {
        return dimensionRule;
    }

    public void setDimensionRule(String dimensionRule) {
        this.dimensionRule = dimensionRule;
    }

    public String getIsRedLine() {
        return isRedLine;
    }

    public void setIsRedLine(String isRedLine) {
        this.isRedLine = isRedLine;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getEvaluateInstance() {
        return evaluateInstance;
    }

    public void setEvaluateInstance(String evaluateInstance) {
        this.evaluateInstance = evaluateInstance;
    }

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
