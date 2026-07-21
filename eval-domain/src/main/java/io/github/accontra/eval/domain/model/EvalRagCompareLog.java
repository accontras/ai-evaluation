package io.github.accontra.eval.domain.model;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("eval_rag_compare_log")
public class EvalRagCompareLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bizId;

    private String sceneCode;

    private String indexCode;

    private String indexName;

    private String dataValue;

    private String vectorResults;

    private String ruleResults;

    private String vectorSimilarities;

    private Integer vectorHit;

    private Integer ruleHit;

    private String groundTruthRel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // ======== getter/setter ========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }

    public String getSceneCode() { return sceneCode; }
    public void setSceneCode(String sceneCode) { this.sceneCode = sceneCode; }

    public String getIndexCode() { return indexCode; }
    public void setIndexCode(String indexCode) { this.indexCode = indexCode; }

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }

    public String getDataValue() { return dataValue; }
    public void setDataValue(String dataValue) { this.dataValue = dataValue; }

    public String getVectorResults() { return vectorResults; }
    public void setVectorResults(String vectorResults) { this.vectorResults = vectorResults; }

    public String getRuleResults() { return ruleResults; }
    public void setRuleResults(String ruleResults) { this.ruleResults = ruleResults; }

    public String getVectorSimilarities() { return vectorSimilarities; }
    public void setVectorSimilarities(String vectorSimilarities) { this.vectorSimilarities = vectorSimilarities; }

    public Integer getVectorHit() { return vectorHit; }
    public void setVectorHit(Integer vectorHit) { this.vectorHit = vectorHit; }

    public Integer getRuleHit() { return ruleHit; }
    public void setRuleHit(Integer ruleHit) { this.ruleHit = ruleHit; }

    public String getGroundTruthRel() { return groundTruthRel; }
    public void setGroundTruthRel(String groundTruthRel) { this.groundTruthRel = groundTruthRel; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
