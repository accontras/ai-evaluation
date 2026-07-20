package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("eval_model")
public class EvalModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String aggregateMode;
    private String status;
    private String dimensions;
    private String dimensionOptions;
    private Integer vn;
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
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAggregateMode() { return aggregateMode; }
    public void setAggregateMode(String aggregateMode) { this.aggregateMode = aggregateMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }
    public String getDimensionOptions() { return dimensionOptions; }
    public void setDimensionOptions(String dimensionOptions) { this.dimensionOptions = dimensionOptions; }
    public Integer getVn() { return vn; }
    public void setVn(Integer vn) { this.vn = vn; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
