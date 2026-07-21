# A2: LLM 可观测性 — 设计与实现

> **状态**: ✅ 已实现 | **日期**: 2026-07-21
> **核心认知**: 后端有 APM，LLM 调用也应该有。不观测的 LLM 就是黑盒。

---

## 一、问题与目标

### 当前痛点

每次 LLM 调用我们只能回答"打分回来了吗？"——无法回答：
- 这次调用花了多少 token？多少钱？
- 延迟正常吗？（P50 vs P95）
- 哪个 Prompt 版本效果最好？
- 有没有某次调用突然变慢或 token 激增？

### 目标

每次 LLM 调用自动记录：模型/版本/token/延迟/错误类型/重试次数。业务代码零侵入。

---

## 二、数据模型

### eval_ai_experiment

```sql
CREATE TABLE eval_ai_experiment (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    experiment_type VARCHAR(32)  NOT NULL,  -- SCORING / EVENT / SUMMARY
    model           VARCHAR(64),            -- deepseek-chat
    prompt_version  VARCHAR(32),            -- v3-fewshot
    scene_code      VARCHAR(64),
    biz_id          VARCHAR(64),
    index_code      VARCHAR(64),
    input_tokens    INT,                    -- 来自 API usage.prompt_tokens
    output_tokens   INT,                    -- 来自 API usage.completion_tokens
    duration_ms     BIGINT,                 -- System.currentTimeMillis() - start
    llm_score       DECIMAL(10,2),          -- LLM 打分
    rule_score      DECIMAL(10,2),          -- 规则引擎打分(对比基线)
    score_diff      DECIMAL(10,2),          -- |llm - rule|
    temperature     DECIMAL(3,2),
    error_type      VARCHAR(64),            -- null=成功, "HTTP_429"=限流, etc.
    retry_count     INT DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scene (scene_code),
    INDEX idx_type (experiment_type),
    INDEX idx_model (model),
    INDEX idx_created (created_at)
);
```

---

## 三、数据采集架构

### 3.1 零侵入原则

```
LlmClient.chat()                    ← 一次修改，全系统受益
  │
  ├─ 自动解析 API response.usage   ← input_tokens / output_tokens
  ├─ 自动计时 duration_ms          ← start = System.currentTimeMillis()
  ├─ 自动捕获 error_type           ← try/catch → error.getClass().getSimpleName()
  │
  └─ 返回 LlmResponse              ← 封装 content + metrics
       │
       ▼
LlmScoringStrategy.recordExperiment()  ← 业务层记录到 DB
```

### 3.2 LlmResponse 设计

```java
public record LlmResponse(
    String content,        // LLM 文本回复
    int inputTokens,       // 输入 token 数
    int outputTokens,      // 输出 token 数
    long durationMs,       // 调用耗时(毫秒)
    String errorType       // null=成功
) {
    public boolean isError() { return errorType != null; }
    public int totalTokens() { return inputTokens + outputTokens; }
}
```

### 3.3 采集点与触发方式

**记录是自动的——每次 LLM 调用自动落库，业务代码不需要显式调用记录方法。**

| 采集点 | 触发时机 | experiment_type | 接入状态 |
|--------|---------|----------------|---------|
| LlmScoringStrategy | 每次 LLM 打分 (H3) | SCORING | ✅ 已接入 |
| LlmEventDetector | 每次 LLM 异常检测 (H4) | EVENT | ⬜ 待接入 |
| AiSummaryService | 每次 AI 总结 (H6) | SUMMARY | ⬜ 待接入 |

调用链: `LlmClient.chat()` 返回 `LlmResponse` → `LlmScoringStrategy.recordExperiment()` 自动写入 DB。
读取链: `GET /experiments/stats` / `GET /prompts/stats` 按需查询。**记录自动，查询按需。**

---

## 四、API

### 4.1 实验统计

`GET /api/v1/evaluation/experiments/stats`

```json
{
  "totalCalls": 72,
  "totalTokens": 48213,
  "avgDurationMs": 1953,
  "errorCount": 0,
  "errorRate": "0.0%"
}
```

### 4.2 按 Prompt 版本聚合

`GET /api/v1/prompts/stats`

```json
{
  "v3-fewshot":   { "calls": 45, "avgDurationMs": 2340, "totalTokens": 30600, "errorRate": "0%" },
  "v2-standards": { "calls": 15, "avgDurationMs": 2180, "totalTokens": 9750, "errorRate": "0%" },
  "v1-base":      { "calls": 12, "avgDurationMs": 2500, "totalTokens": 7863, "errorRate": "0%" }
}
```

---

## 七、当前状态与待完善

### 已实现

- [x] `eval_ai_experiment` 表 + Entity + Mapper
- [x] `LlmResponse` 封装 (content + tokens + duration + errorType)
- [x] `LlmClient.chat()` 自动提取 API usage
- [x] `LlmScoringStrategy.recordExperiment()` 自动记录 SCORING 调用
- [x] `GET /experiments/stats` — 全局统计
- [x] `GET /prompts/stats` — 按版本聚合

### 待完善 (A2.2)

- [ ] `LlmEventDetector` 接入 recordExperiment (EVENT 类型)
- [ ] `AiSummaryService` 接入 recordExperiment (SUMMARY 类型)
- [ ] P95 延迟统计 (当前只有平均值)
- [ ] 异常检测: 单次 token 消耗 > 均值 3σ → 标记
- [ ] 成本估算: tokens × 单价 → 累计费用
- [ ] Dashboard 展示实验统计面板 (当前只有 API)

---

## 六、关键设计决策

## 五、API 响应结构

### 5.1 LlmResponse (LlmClient → 业务层)

每次 LLM 调用返回的原始结构：

```json
{
  "content": "{\"scores\":[{\"indexCode\":\"COST_DEV\",\"score\":70,\"reason\":\"...\"}]}",
  "inputTokens": 280,
  "outputTokens": 85,
  "durationMs": 1484,
  "errorType": null,
  "totalTokens": 365,
  "isError": false
}
```

### 5.2 GET /experiments/stats

```json
{
  "code": "00000",
  "message": "成功",
  "data": {
    "totalCalls": 72,
    "totalTokens": 48213,
    "avgDurationMs": 1953,
    "errorCount": 0,
    "errorRate": "0.0%"
  }
}
```

### 5.3 GET /prompts/stats

```json
{
  "code": "00000",
  "data": {
    "v3-fewshot":   { "calls": 45, "avgDurationMs": 2340, "totalTokens": 30600, "errorRate": "0.0%" },
    "v2-standards": { "calls": 15, "avgDurationMs": 2180, "totalTokens": 9750,  "errorRate": "0.0%" },
    "v1-base":      { "calls": 12, "avgDurationMs": 2500, "totalTokens": 7863,  "errorRate": "0.0%" }
  }
}
```

### 5.4 eval_ai_experiment 表记录示例

```json
{
  "id": 1,
  "experimentType": "SCORING",
  "model": "deepseek-chat",
  "promptVersion": "v3-fewshot",
  "sceneCode": "LOGISTICS-2026Q2",
  "bizId": "EXP-001",
  "inputTokens": 280,
  "outputTokens": 85,
  "durationMs": 1484,
  "llmScore": 70.00,
  "temperature": 0.30,
  "errorType": null,
  "createdAt": "2026-07-21T10:44:38"
}
```

---

## 六、关键设计决策

### 为什么在 LlmClient 层采集而不是业务层？

**决策**: 在 `LlmClient.chat()` 返回 `LlmResponse`，业务层决定是否记录。

**理由**:
1. API usage 只有 LlmClient 能拿到（在 HTTP response body 里）
2. 业务层可以选择记录粒度（SCORING 每个指标记一条 vs 一次调用记一条）
3. 测试代码用 `LlmClient` 但不需要记录实验——由业务层 `recordExperiment()` 控制

### 为什么不用 AOP/拦截器？

当前规模不需要。一个 `recordExperiment()` 方法 + 手动调用足够。当调用点 > 5 时再考虑 AOP。

### cost 字段为什么没加？

当前 DeepSeek API 价格极低（¥1/百万 token），累计成本 < ¥0.1。加 cost 字段的工程投入 > 当前价值。后续价格敏感时再加。
