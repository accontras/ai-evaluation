package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("eval_object_log_msg")
public class EvalObjectLogMsg {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long objectLogId;

    private String header;

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

    public Long getObjectLogId() {
        return objectLogId;
    }

    public void setObjectLogId(Long objectLogId) {
        this.objectLogId = objectLogId;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
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
