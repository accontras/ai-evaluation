package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("eval_prompt_template")
public class EvalPromptTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String promptKey;
    private String version;
    private String templateText;
    private String description;
    private Integer isActive;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getPromptKey() { return promptKey; }
    public void setPromptKey(String v) { promptKey = v; }
    public String getVersion() { return version; }
    public void setVersion(String v) { version = v; }
    public String getTemplateText() { return templateText; }
    public void setTemplateText(String v) { templateText = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer v) { isActive = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}
