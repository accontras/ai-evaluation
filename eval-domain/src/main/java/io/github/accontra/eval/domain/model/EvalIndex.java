package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("eval_index")
public class EvalIndex {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String catalog;

    private String unit;

    private String indexFieldCode;

    private String calculateType;

    private String status;

    private String calculateRule;

    private String relateIndex;

    private Integer layer;

    private String dimensions;

    private String queryDataSet;

    private String dimensionOptions;

    private String filterOptions;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getIndexFieldCode() {
        return indexFieldCode;
    }

    public void setIndexFieldCode(String indexFieldCode) {
        this.indexFieldCode = indexFieldCode;
    }

    public String getCalculateType() {
        return calculateType;
    }

    public void setCalculateType(String calculateType) {
        this.calculateType = calculateType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCalculateRule() {
        return calculateRule;
    }

    public void setCalculateRule(String calculateRule) {
        this.calculateRule = calculateRule;
    }

    public String getRelateIndex() {
        return relateIndex;
    }

    public void setRelateIndex(String relateIndex) {
        this.relateIndex = relateIndex;
    }

    public Integer getLayer() {
        return layer;
    }

    public void setLayer(Integer layer) {
        this.layer = layer;
    }

    public String getDimensions() {
        return dimensions;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public String getQueryDataSet() {
        return queryDataSet;
    }

    public void setQueryDataSet(String queryDataSet) {
        this.queryDataSet = queryDataSet;
    }

    public String getDimensionOptions() {
        return dimensionOptions;
    }

    public void setDimensionOptions(String dimensionOptions) {
        this.dimensionOptions = dimensionOptions;
    }

    public String getFilterOptions() {
        return filterOptions;
    }

    public void setFilterOptions(String filterOptions) {
        this.filterOptions = filterOptions;
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
