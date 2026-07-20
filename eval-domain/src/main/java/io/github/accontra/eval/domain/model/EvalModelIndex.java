package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("eval_model_index")
public class EvalModelIndex {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelId;
    private Long stageId;
    private Long indexId;
    private Integer sn;
    private BigDecimal scoreCap;
    private BigDecimal scoreFloor;
    private String queryDataSet;
    private String dimensionOptions;
    private String dataSource;
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
    public Integer getSn() { return sn; }
    public void setSn(Integer sn) { this.sn = sn; }
    public BigDecimal getScoreCap() { return scoreCap; }
    public void setScoreCap(BigDecimal scoreCap) { this.scoreCap = scoreCap; }
    public BigDecimal getScoreFloor() { return scoreFloor; }
    public void setScoreFloor(BigDecimal scoreFloor) { this.scoreFloor = scoreFloor; }
    public String getQueryDataSet() { return queryDataSet; }
    public void setQueryDataSet(String queryDataSet) { this.queryDataSet = queryDataSet; }
    public String getDimensionOptions() { return dimensionOptions; }
    public void setDimensionOptions(String dimensionOptions) { this.dimensionOptions = dimensionOptions; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
