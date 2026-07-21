package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("eval_ai_experiment")
public class EvalAiExperiment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String experimentType;
    private String model;
    private String promptVersion;
    private String sceneCode;
    private String bizId;
    private String indexCode;
    private Integer inputTokens;
    private Integer outputTokens;
    private Long durationMs;
    private BigDecimal llmScore;
    private BigDecimal ruleScore;
    private BigDecimal scoreDiff;
    private BigDecimal temperature;
    private String errorType;
    private Integer retryCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getExperimentType() { return experimentType; }
    public void setExperimentType(String v) { experimentType = v; }
    public String getModel() { return model; }
    public void setModel(String v) { model = v; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String v) { promptVersion = v; }
    public String getSceneCode() { return sceneCode; }
    public void setSceneCode(String v) { sceneCode = v; }
    public String getBizId() { return bizId; }
    public void setBizId(String v) { bizId = v; }
    public String getIndexCode() { return indexCode; }
    public void setIndexCode(String v) { indexCode = v; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer v) { inputTokens = v; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer v) { outputTokens = v; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long v) { durationMs = v; }
    public BigDecimal getLlmScore() { return llmScore; }
    public void setLlmScore(BigDecimal v) { llmScore = v; }
    public BigDecimal getRuleScore() { return ruleScore; }
    public void setRuleScore(BigDecimal v) { ruleScore = v; }
    public BigDecimal getScoreDiff() { return scoreDiff; }
    public void setScoreDiff(BigDecimal v) { scoreDiff = v; }
    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal v) { temperature = v; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String v) { errorType = v; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer v) { retryCount = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}
