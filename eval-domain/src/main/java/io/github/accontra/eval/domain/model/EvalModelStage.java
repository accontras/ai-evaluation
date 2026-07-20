package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("eval_model_stage")
public class EvalModelStage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelId;
    private Long parentId;
    private String type;
    private Integer level;
    private String code;
    private String name;
    private Integer sn;
    private Integer weight;
    private Integer priority;
    private String aggregateMode;
    private String routeCondition;
    private BigDecimal defaultScore;
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
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getSn() { return sn; }
    public void setSn(Integer sn) { this.sn = sn; }
    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public String getAggregateMode() { return aggregateMode; }
    public void setAggregateMode(String aggregateMode) { this.aggregateMode = aggregateMode; }
    public String getRouteCondition() { return routeCondition; }
    public void setRouteCondition(String v) { routeCondition = v; }
    public BigDecimal getDefaultScore() { return defaultScore; }
    public void setDefaultScore(BigDecimal defaultScore) { this.defaultScore = defaultScore; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
