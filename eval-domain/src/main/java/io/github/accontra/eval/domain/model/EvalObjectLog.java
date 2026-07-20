package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("eval_object_log")
public class EvalObjectLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskLogId;

    private String sceneCode;

    private String modelCode;

    private String targetCode;

    private BigDecimal totalScore;

    private String riskLevel;

    private String grade;

    private String gradeMappingMode;

    private BigDecimal appealAdjustedScore;

    private String evidenceChain;

    private String summary;

    private String summaryStatus;

    private Integer evalRank;

    private Integer rankTotal;

    private String workerId;

    private String evalPeriod;

    private String appealSource;

    private String status;

    @TableLogic
    private Integer enabled;

    private String memo;

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

    public Long getTaskLogId() {
        return taskLogId;
    }

    public void setTaskLogId(Long taskLogId) {
        this.taskLogId = taskLogId;
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

    public String getTargetCode() {
        return targetCode;
    }

    public void setTargetCode(String targetCode) {
        this.targetCode = targetCode;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getGradeMappingMode() {
        return gradeMappingMode;
    }

    public void setGradeMappingMode(String gradeMappingMode) {
        this.gradeMappingMode = gradeMappingMode;
    }

    public BigDecimal getAppealAdjustedScore() {
        return appealAdjustedScore;
    }

    public void setAppealAdjustedScore(BigDecimal appealAdjustedScore) {
        this.appealAdjustedScore = appealAdjustedScore;
    }

    public String getEvidenceChain() {
        return evidenceChain;
    }

    public void setEvidenceChain(String evidenceChain) {
        this.evidenceChain = evidenceChain;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSummaryStatus() {
        return summaryStatus;
    }

    public void setSummaryStatus(String summaryStatus) {
        this.summaryStatus = summaryStatus;
    }

    public Integer getEvalRank() {
        return evalRank;
    }

    public void setEvalRank(Integer evalRank) {
        this.evalRank = evalRank;
    }

    public Integer getRankTotal() {
        return rankTotal;
    }

    public void setRankTotal(Integer rankTotal) {
        this.rankTotal = rankTotal;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getEvalPeriod() {
        return evalPeriod;
    }

    public void setEvalPeriod(String evalPeriod) {
        this.evalPeriod = evalPeriod;
    }

    public String getAppealSource() {
        return appealSource;
    }

    public void setAppealSource(String appealSource) {
        this.appealSource = appealSource;
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

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
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
