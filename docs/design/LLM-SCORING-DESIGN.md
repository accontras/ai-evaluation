# LLM-as-Judge 打分 — 详细设计与实现

> **定位**: LLM 语义理解 + 规则引擎验证。AI 坐主桌，规则引擎当镜子。
> **版本**: v1.0 | **日期**: 2026-07-20

---

## 一、设计哲学：为什么 LLM 先打分？

### 传统规则引擎的局限

```
规则引擎打分逻辑:
  IF fill_rate < 60  → 0分
  IF 60 ≤ fill_rate < 80 → 60分
  IF 80 ≤ fill_rate < 95 → 80分
  IF fill_rate ≥ 95 → 100分
```

**问题**：
1. **硬边界悖论**：85% 和 95% 的本质差异被"同一区间"掩盖；79.9% 和 80% 刚好跨边界，差 20 分
2. **盲区**：规则只能覆盖预设条件。如果业务出现新场景（比如"疫情期间填报率下降但有合理解释"），规则必然误判
3. **维护成本**：每新增一个业务场景，需要重新定义全部阈值区间

### LLM-as-Judge 的优势

```
LLM 打分逻辑:
  输入: "填报及时率 85.5%，参考标准: 通常 90% 以上为优秀"
  输出: 75分, 理由: "85.5% 略低于常见目标值，存在一定延迟风险，但未严重超标"
```

**优势**：
1. **语义理解**：不需要精确阈值，理解"85.5% 算中等偏上"的业务语义
2. **上下文感知**：能结合指标间的关联关系（费用偏差 + 异常次数 → 判断是否系统性问题）
3. **零配置**：不需要为每个指标预设评分区间，LLM 自带领域知识
4. **可解释性**：每个分数都附带自然语言理由

### 为什么还需要规则引擎？

> **LLM 的判断不可审计**。同一个指标，两次调用可能得到 75 和 80 分。规则引擎提供**确定性的对比基线**——当 LLM 和规则差异显著时，触发人工关注。对比数据本身就是最有价值的资产。

---

## 二、架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                     POST /evaluation/execute                 │
└──────────────────────────┬──────────────────────────────────┘
                           │
              ┌────────────▼────────────┐
              │  H3 LlmCalculateScores  │
              │       Handler           │
              └─────┬─────────┬─────────┘
                    │         │
         ┌──────────▼──┐  ┌──▼──────────────┐
         │ LlmScoring  │  │ RuleScoreStrategy│
         │ Strategy    │  │   (JEXL)         │
         └──────┬──────┘  └──────┬───────────┘
                │                │
         ┌──────▼──────┐         │
         │  LlmClient   │         │
         │ chatForJson()│         │
         └──────┬──────┘         │
                │                │
         ┌──────▼──────┐         │
         │  DeepSeek    │         │
         │  API         │         │
         └──────┬──────┘         │
                │                │
         scores_llm          scores_rule
                │                │
                └───────┬────────┘
                        │
              ┌─────────▼──────────┐
              │ DualChannelScoring │
              │    Service         │
              │   .compare()       │
              └─────────┬──────────┘
                        │
              ┌─────────▼──────────┐
              │  IndicatorDiff[]   │
              │  TRIVIAL/NOTABLE/  │
              │  SIGNIFICANT       │
              └────────────────────┘
```

---

## 三、LlmClient 基础设施

### 3.1 接口设计

```java
// 文件: eval-infrastructure/.../llm/LlmClient.java
public class LlmClient {
    // 基础聊天 — 返回文本
    public String chat(String systemPrompt, String userPrompt)

    // 结构化输出 — 自动提取 JSON
    public JSONObject chatForJson(String systemPrompt, String userPrompt)
}
```

### 3.2 请求格式 (OpenAI 兼容)

```json
POST https://api.deepseek.com/v1/chat/completions
{
  "model": "deepseek-chat",
  "temperature": 0.3,
  "max_tokens": 2048,
  "messages": [
    { "role": "system", "content": "<系统提示词>" },
    { "role": "user",   "content": "<用户提示词>" }
  ]
}
```

**参数选择**：
| 参数 | 值 | 理由 |
|------|-----|------|
| temperature | 0.3 | 低随机性——评估需要一致性，不要创意 |
| max_tokens | 2048 | 3个指标打分+理由通常在 500 tokens 内 |
| timeout | 120s | DeepSeek 通常 2-5 秒返回 |

### 3.3 JSON 提取

```java
public JSONObject chatForJson(String systemPrompt, String userPrompt) {
    String raw = chat(systemPrompt, userPrompt);
    // LLM 可能用 ```json 包裹，需要提取纯 JSON
    String jsonStr = raw;
    if (raw.contains("```json")) {
        jsonStr = raw.substring(raw.indexOf("```json") + 7, raw.lastIndexOf("```"));
    } else if (raw.contains("```")) {
        jsonStr = raw.substring(raw.indexOf("```") + 3, raw.lastIndexOf("```"));
    }
    return JSONUtil.parseObj(jsonStr.trim());
}
```

### 3.4 配置

```yaml
# application.yml
llm:
  provider: deepseek
  base-url: https://api.deepseek.com
  model: deepseek-chat
  api-key: ${LLM_API_KEY:sk-xxx}
```

Spring Boot 配置绑定：
```java
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private String provider = "deepseek";
    private String apiKey;
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-chat";
}
```

---

## 四、LlmScoringStrategy 打分策略

### 4.1 核心方法

```java
// 文件: eval-application/.../strategy/LlmScoringStrategy.java
public Map<String, ScoreResult> scoreAll(EvaluationContext ctx)
```

### 4.2 Prompt 设计

**系统提示词**（角色 + 格式约束）：

```
你是一个企业级业务评估分析师。你会收到一个评估对象的多个指标数据，
以及每个指标的评分标准。请对每个指标独立打分（0-100 分），并给出简短理由。

评分规则：
  - 0-100 分，分数越高表示该指标表现越好
  - 严格参考提供的评分标准区间来打分
  - 如果实际值跨区间，考虑其更接近哪个区间
  - 不要机械地按区间映射，考虑指标的改善/恶化趋势

回复 MUST 是严格的 JSON 格式:
{ "scores": [{"indexCode": "xxx", "score": 85, "reason": "..."}], "overallComment": "..." }
```

**用户提示词模板**（v2 增强：注入评分标准）：

```
## 评估对象
- 对象ID: LGS-004

## 指标数据与评分标准
| 指标编码 | 指标名称 | 实际值 | 评分标准 (min≤值<max → 得分) |
|---------|---------|-------|-------------------------------|
| COST_DEV | 费用偏差率 | 9.2 | 0≤值<5→90分; 5≤值<10→75分; 10≤值<15→55分; 15≤值<999→30分 |
| ABNORM_CNT | 异常波动次数 | 2 | 0≤值<0→100分; 1≤值<4→85分; 4≤值<7→65分; 7≤值<999→40分 |
| FILL_RATE | 填报及时率 | 85.5 | 95≤值<101→95分; 85≤值<95→80分; 75≤值<85→60分; 0≤值<75→35分 |

请对以上 3 个指标逐一打分。
```

**数据来源**: `eval_model_standard` 表，按 `model_id + index_id + priority` 排序后格式化。

### 4.3 Prompt 设计原则

| 原则 | 实现 |
|------|------|
| **角色锚定** | 系统提示词第一句明确角色："企业级业务评估分析师" |
| **格式严格** | "MUST 是严格的 JSON" + 给出完整 schema |
| **反机械化** | "不要机械地按区间映射，考虑趋势和上下文"——引导 LLM 做语义判断 |
| **温度控制** | temperature=0.3，降低创造性 |
| **数据隔离** | 只传指标编码+名称+实际值，不传内部ID/数据库字段 |

### 4.4 降级策略

```java
// 正常路径
try {
    JSONObject json = llmClient.chatForJson(SYSTEM_PROMPT, userPrompt);
    // 解析 JSON → scores
    return results;
} catch (Exception e) {
    // 降级路径: 所有指标默认 70 分
    log.error("[LLM] 打分失败, 降级处理", e);
    return degradedScores(ctx);
}

private Map<String, ScoreResult> degradedScores(EvaluationContext ctx) {
    // 每个指标返回 70 分 + "LLM 不可用，默认 70 分"
}
```

**降级触发条件**：
- 网络超时（120s 未响应）
- API 返回非 200
- JSON 解析失败（LLM 返回格式不符合 schema）
- DeepSeek 服务不可用

**降级分数选择**：70 分是一个"不功不过"的中性值——比 50（暗示很烂）和 90（暗示很好）都更安全，不会因为 LLM 不可用导致错误的评估结论。

### 4.5 实际输出示例

```json
{
  "scores": [
    {
      "indexCode": "COST_DEV",
      "indexName": "费用偏差率",
      "score": 70.00,
      "reason": "费用偏差率9.2%处于中等偏高水平，通常偏差率在5%以内为良好，超过10%则需关注。该值接近警戒线，但未严重超标。"
    },
    {
      "indexCode": "ABNORM_CNT",
      "indexName": "异常波动次数",
      "score": 85.00,
      "reason": "异常波动次数为2次，属于较低水平，表明业务运行相对稳定。"
    },
    {
      "indexCode": "FILL_RATE",
      "indexName": "填报及时率",
      "score": 75.00,
      "reason": "填报及时率85.5%略低于常见目标值（如90%），存在一定延迟风险，但未达到严重程度。"
    }
  ],
  "overallComment": "该对象整体处于中等水平，异常波动较少，但费用控制和填报及时性有提升空间。"
}
```

---

## 五、DualChannelScoringService 双通道对比

### 5.1 对比算法

```java
// 文件: eval-application/.../strategy/DualChannelScoringService.java
public CompareResult compare(EvaluationContext ctx) {
    var llmScores = llmStrategy.scoreAll(ctx);     // LLM 打分
    var ruleScores = ruleStrategy.scoreAll(ctx);    // 规则引擎打分

    // 逐指标对比
    for (var entry : ruleScores.entrySet()) {
        var rule = entry.getValue();
        var llm = llmScores.get(entry.getKey());
        if (llm == null) continue;

        double pctDiff = Math.abs(rule.score() - llm.score()) / 100.0;

        String level;
        if (pctDiff < 0.05)  level = "TRIVIAL";       // 差异 < 5分
        else if (pctDiff < 0.15) level = "NOTABLE";   // 差异 5-15分
        else level = "SIGNIFICANT";                     // 差异 ≥ 15分

        diffs.add(new IndicatorDiff(...));
    }
}
```

### 5.2 差异分级

| 级别 | 阈值 | 含义 | 行动 |
|------|------|------|------|
| TRIVIAL | < 5 分 | 正常波动 | 无需关注 |
| NOTABLE | 5-15 分 | AI 可能看到规则看不到的东西 | 值得关注 |
| SIGNIFICANT | ≥ 15 分 | 需要人工仲裁 | 要么规则太死，要么 AI 误判 |

### 5.3 对比数据存储

每次评估后，对比结果写入 `eval_indicator_log` 表：

| 字段 | 来源 |
|------|------|
| `llm_score` | LlmScoringStrategy.scoreAll() |
| `rule_score` | RuleScoreStrategy.scoreAll() |
| `score_diff` | abs(llm - rule) |
| `diff_level` | TRIVIAL / NOTABLE / SIGNIFICANT |
| `llm_reason` | LLM 返回的理由文本 |

---

## 六、数据流全链路

```
1. EvaluationContext.rawValues = {"cost_deviation": 9.2, "abnormal_count": 2, "fill_rate": 85.5}
                │
2. LlmScoringStrategy.scoreAll(ctx)
                │
   ┌────────────▼────────────┐
   │ 构建 Markdown 指标表格    │  indexBaseMap: {1→COST_DEV, 2→ABNORM_CNT, 3→FILL_RATE}
   │ | 指标编码 | 名称 | 实际值 |  rawValues:  {cost_deviation:9.2, ...}
   │ | COST_DEV | ... | 9.2   |  → 渲染为 Markdown Table
   │ | ABNORM_CNT| ...| 2     |
   │ | FILL_RATE| ... | 85.5  |
   └────────────┬────────────┘
                │
   ┌────────────▼────────────┐
   │ PromptTemplate.render()  │  {{bizId}}, {{indicatorTable}}, {{count}}
   │ → 完整 userPrompt        │
   └────────────┬────────────┘
                │
   ┌────────────▼────────────┐
   │ LlmClient.chatForJson()  │  POST /v1/chat/completions
   │ SYSTEM_PROMPT            │  → DeepSeek API
   │ + userPrompt             │  ← JSON response
   └────────────┬────────────┘
                │
   ┌────────────▼────────────┐
   │ 解析 JSON                │  scores[].indexCode → ScoreResult
   │ { scores: [...] }        │  Map<String, ScoreResult>
   └────────────┬────────────┘
                │
3. ctx.setLlmScores(results)      → EvaluationContext.llmScores
4. DualChannel.compare(ctx)       → 同时跑规则引擎 + 对比
5. TreeAggregator.aggregate()     → 自底向上聚合 (LLM分数为leaf输入)
6. H6 落库                        → eval_indicator_log (含对比数据)
7. Controller 返回                 → ExecuteEvaluationResponse
```

---

## 七、实现文件索引

| 层 | 文件 | 职责 |
|----|------|------|
| 基础设施 | `LlmClient.java` | HTTP 客户端, OpenAI 兼容 API 调用 |
| 基础设施 | `LlmConfig.java` | Spring Bean 装配 |
| 基础设施 | `LlmProperties.java` | `application.yml` → 配置对象 |
| 基础设施 | `PromptTemplate.java` | `{{variable}}` 模板渲染 |
| 策略 | `LlmScoringStrategy.java` | LLM-as-Judge 核心逻辑 + 降级 |
| 策略 | `RuleScoreStrategy.java` | JEXL 规则引擎打分 (对比基线) |
| 策略 | `DualChannelScoringService.java` | 双通道对比 + 差异分级 |
| Handler | `LlmCalculateScoresHandler.java` | H3: 编排打分+树聚合 |
| 测试 | `LlmScoringTest.java` | LLM 打分单元测试 |
| 测试 | `DualChannelTest.java` | 双通道对比测试 |

---

## 八、配置参考

```yaml
# 当前使用的配置 (eval-boot/src/main/resources/application.yml)
llm:
  provider: deepseek          # 模型提供商
  base-url: https://api.deepseek.com
  model: deepseek-chat        # 推荐: deepseek-chat (性价比最高)
  api-key: ${LLM_API_KEY:sk-xxx}

# 可替换为其他 OpenAI 兼容模型:
#   OpenAI:      base-url: https://api.openai.com, model: gpt-4o
#   Qwen/本地:   base-url: http://localhost:11434, model: qwen2.5:7b
```
