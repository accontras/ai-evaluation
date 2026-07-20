package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("eval_indicator_log_msg")
public class EvalIndicatorLogMsg {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long indicatorLogId;

    private String paramIn;

    private String paramOut;

    private String errorMsg;

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

    public Long getIndicatorLogId() {
        return indicatorLogId;
    }

    public void setIndicatorLogId(Long indicatorLogId) {
        this.indicatorLogId = indicatorLogId;
    }

    public String getParamIn() {
        return paramIn;
    }

    public void setParamIn(String paramIn) {
        this.paramIn = paramIn;
    }

    public String getParamOut() {
        return paramOut;
    }

    public void setParamOut(String paramOut) {
        this.paramOut = paramOut;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
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
