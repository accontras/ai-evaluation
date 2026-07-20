package io.github.accontra.eval.application.pipeline;

import io.github.accontra.eval.domain.model.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Pipeline 数据总线 — 贯穿全部 Handler。
 * 每个 Handler 从 Context 读取输入，执行后将产出写回 Context。
 */
public class EvaluationContext {

    // ===== 输入 =====
    private String sceneCode;
    private String bizId;
    private String dataPeriod;
    private Map<String, Object> rawData;
    private boolean appealRecalc;

    // ===== H1 产出 =====
    private EvalScene scene;
    private EvalModel model;
    private List<EvalModelStage> stages;
    private List<EvalModelIndex> modelIndices;
    private List<EvalModelStandard> modelStandards;
    private List<EvalModelEvent> modelEvents;
    private EvalTarget target;
    private Map<String, EvalDimension> dimDefinitions;
    private Map<String, EvalIndex> indexBaseMap;
    private List<EvalGradeMapping> gradeMappings;

    // ===== H2 产出 =====
    private Map<String, Object> rawValues;
    private Map<String, Object> attrValues;

    // ===== H3 产出 =====
    private BigDecimal totalScore;
    // TODO: StageResult / IndicatorResult

    // ===== H4 产出 =====
    private boolean blocked;
    private BigDecimal adjustedTotalScore;
    // TODO: EventResult list

    // ===== H5 产出 =====
    private BigDecimal appealAdjustedScore;

    // ===== H6 产出 =====
    private String grade;
    private String riskLevel;
    private String summary;

    // ===== getters & setters =====
    public String getSceneCode() { return sceneCode; }
    public void setSceneCode(String v) { sceneCode = v; }
    public String getBizId() { return bizId; }
    public void setBizId(String v) { bizId = v; }
    public String getDataPeriod() { return dataPeriod; }
    public void setDataPeriod(String v) { dataPeriod = v; }
    public Map<String, Object> getRawData() { return rawData; }
    public void setRawData(Map<String, Object> v) { rawData = v; }
    public boolean isAppealRecalc() { return appealRecalc; }
    public void setAppealRecalc(boolean v) { appealRecalc = v; }

    public EvalScene getScene() { return scene; }
    public void setScene(EvalScene v) { scene = v; }
    public EvalModel getModel() { return model; }
    public void setModel(EvalModel v) { model = v; }
    public List<EvalModelStage> getStages() { return stages; }
    public void setStages(List<EvalModelStage> v) { stages = v; }
    public List<EvalModelIndex> getModelIndices() { return modelIndices; }
    public void setModelIndices(List<EvalModelIndex> v) { modelIndices = v; }
    public List<EvalModelStandard> getModelStandards() { return modelStandards; }
    public void setModelStandards(List<EvalModelStandard> v) { modelStandards = v; }
    public List<EvalModelEvent> getModelEvents() { return modelEvents; }
    public void setModelEvents(List<EvalModelEvent> v) { modelEvents = v; }
    public EvalTarget getTarget() { return target; }
    public void setTarget(EvalTarget v) { target = v; }
    public Map<String, EvalDimension> getDimDefinitions() { return dimDefinitions; }
    public void setDimDefinitions(Map<String, EvalDimension> v) { dimDefinitions = v; }
    public Map<String, EvalIndex> getIndexBaseMap() { return indexBaseMap; }
    public void setIndexBaseMap(Map<String, EvalIndex> v) { indexBaseMap = v; }
    public List<EvalGradeMapping> getGradeMappings() { return gradeMappings; }
    public void setGradeMappings(List<EvalGradeMapping> v) { gradeMappings = v; }

    public Map<String, Object> getRawValues() { return rawValues; }
    public void setRawValues(Map<String, Object> v) { rawValues = v; }
    public Map<String, Object> getAttrValues() { return attrValues; }
    public void setAttrValues(Map<String, Object> v) { attrValues = v; }

    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal v) { totalScore = v; }
    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean v) { blocked = v; }
    public BigDecimal getAdjustedTotalScore() { return adjustedTotalScore; }
    public void setAdjustedTotalScore(BigDecimal v) { adjustedTotalScore = v; }
    public BigDecimal getAppealAdjustedScore() { return appealAdjustedScore; }
    public void setAppealAdjustedScore(BigDecimal v) { appealAdjustedScore = v; }
    public String getGrade() { return grade; }
    public void setGrade(String v) { grade = v; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String v) { riskLevel = v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { summary = v; }
}
