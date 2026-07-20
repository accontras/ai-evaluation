package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("eval_scene")
public class EvalScene {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long modelId;

    private String name;

    private String targetCode;

    private String status;

    private String aggregateMode;

    private String evaluateMode;

    private String callbackApi;

    private String callbackToken;

    private String callbackBodyTemplate;

    private String callbackParams;

    private Integer appealWindowDays;

    private String gradeMappingMode;

    private String gradeConfig;

    private String defaultRouteBranch;

    private Integer redLineType;

    private String rankRange;

    private String rankType;

    private String dimensionField;

    private Integer defaultEventScore;

    private String options;

    private Integer vn;

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTargetCode() {
        return targetCode;
    }

    public void setTargetCode(String targetCode) {
        this.targetCode = targetCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAggregateMode() {
        return aggregateMode;
    }

    public void setAggregateMode(String aggregateMode) {
        this.aggregateMode = aggregateMode;
    }

    public String getEvaluateMode() {
        return evaluateMode;
    }

    public void setEvaluateMode(String evaluateMode) {
        this.evaluateMode = evaluateMode;
    }

    public String getCallbackApi() {
        return callbackApi;
    }

    public void setCallbackApi(String callbackApi) {
        this.callbackApi = callbackApi;
    }

    public String getCallbackToken() {
        return callbackToken;
    }

    public void setCallbackToken(String callbackToken) {
        this.callbackToken = callbackToken;
    }

    public String getCallbackBodyTemplate() {
        return callbackBodyTemplate;
    }

    public void setCallbackBodyTemplate(String callbackBodyTemplate) {
        this.callbackBodyTemplate = callbackBodyTemplate;
    }

    public String getCallbackParams() {
        return callbackParams;
    }

    public void setCallbackParams(String callbackParams) {
        this.callbackParams = callbackParams;
    }

    public Integer getAppealWindowDays() {
        return appealWindowDays;
    }

    public void setAppealWindowDays(Integer appealWindowDays) {
        this.appealWindowDays = appealWindowDays;
    }

    public String getGradeMappingMode() {
        return gradeMappingMode;
    }

    public void setGradeMappingMode(String gradeMappingMode) {
        this.gradeMappingMode = gradeMappingMode;
    }

    public String getGradeConfig() {
        return gradeConfig;
    }

    public void setGradeConfig(String gradeConfig) {
        this.gradeConfig = gradeConfig;
    }

    public String getDefaultRouteBranch() {
        return defaultRouteBranch;
    }

    public void setDefaultRouteBranch(String defaultRouteBranch) {
        this.defaultRouteBranch = defaultRouteBranch;
    }

    public Integer getRedLineType() {
        return redLineType;
    }

    public void setRedLineType(Integer redLineType) {
        this.redLineType = redLineType;
    }

    public String getRankRange() {
        return rankRange;
    }

    public void setRankRange(String rankRange) {
        this.rankRange = rankRange;
    }

    public String getRankType() {
        return rankType;
    }

    public void setRankType(String rankType) {
        this.rankType = rankType;
    }

    public String getDimensionField() {
        return dimensionField;
    }

    public void setDimensionField(String dimensionField) {
        this.dimensionField = dimensionField;
    }

    public Integer getDefaultEventScore() {
        return defaultEventScore;
    }

    public void setDefaultEventScore(Integer defaultEventScore) {
        this.defaultEventScore = defaultEventScore;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public Integer getVn() {
        return vn;
    }

    public void setVn(Integer vn) {
        this.vn = vn;
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
