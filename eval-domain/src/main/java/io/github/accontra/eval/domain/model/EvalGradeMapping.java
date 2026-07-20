package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("eval_grade_mapping")
public class EvalGradeMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sceneId;

    private String mappingMode;

    private String groupFields;

    private String orderByFields;

    private String rankDirection;

    private String grade;

    private BigDecimal lowerBound;

    private BigDecimal upperBound;

    private Integer priority;

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

    public Long getSceneId() {
        return sceneId;
    }

    public void setSceneId(Long sceneId) {
        this.sceneId = sceneId;
    }

    public String getMappingMode() {
        return mappingMode;
    }

    public void setMappingMode(String mappingMode) {
        this.mappingMode = mappingMode;
    }

    public String getGroupFields() {
        return groupFields;
    }

    public void setGroupFields(String groupFields) {
        this.groupFields = groupFields;
    }

    public String getOrderByFields() {
        return orderByFields;
    }

    public void setOrderByFields(String orderByFields) {
        this.orderByFields = orderByFields;
    }

    public String getRankDirection() {
        return rankDirection;
    }

    public void setRankDirection(String rankDirection) {
        this.rankDirection = rankDirection;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public BigDecimal getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(BigDecimal lowerBound) {
        this.lowerBound = lowerBound;
    }

    public BigDecimal getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(BigDecimal upperBound) {
        this.upperBound = upperBound;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
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
