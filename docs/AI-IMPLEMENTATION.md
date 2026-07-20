# AI 功能实现文档

> **核心哲学**: AI 坐主桌，规则引擎当镜子。LLM 先打分，规则引擎并行跑来做对比。

## 一张表说清楚

| Handler | AI 替代? | 为什么 | 实现类 |
|---------|---------|--------|--------|
| H1 加载配置 | ❌ | 读数据库，AI 没价值 | `ValidateAndLoadModelHandler` |
| H2 提取指标值 | ❌ | 字段映射，确定性操作 | `FetchIndicatorValuesHandler` |
| **H3 标准匹配+打分** | **★ LLM-as-Judge** | 语义理解，上下文判断 | `LlmScoringStrategy` |
| H3 派生指标 | ❌ | 数学运算，LLM 会算错 | `ExpressionUtil` (JEXL) |
| **H3 树聚合** | **❌ 永不替代** | 加权求和必须确定，审计底线 | `TreeAggregator` |
| **H4 事件/红线** | **★ LLM 语义检测** | 异常不靠穷举规则 | `LlmEventDetector` |
| H5 申诉改分 | ❌ | 人工决策，系统只执行 | `AppealController` |
| H6 等级映射 | ❌ | 分数→等级是查表 | `EvalGradeMapping` |
| **H6 总结** | **★ LLM 多轮对话** | 生成+自审 | `AiSummaryService` |
| Ranking | ❌ | 数学排序 | `RankingService` |

---

## 一、LLM 客户端基础设施

**文件**: `eval-infrastructure/.../llm/LlmClient.java`

```
┌──────────────┐     OpenAI 兼容 API      ┌─────────────────┐
│  LlmClient   │ ───────────────────────→ │  DeepSeek / GLM  │
│              │  POST /v1/chat/completions│  / OpenAI / 本地 │
│  chat()      │ ←─────────────────────── │                  │
│  chatForJson()│     JSON response        └─────────────────┘
└──────────────┘
```

**配置** (`application.yml`):
```yaml
llm:
  provider: deepseek
  base-url: https://api.deepseek.com
  model: deepseek-chat
  api-key: ${LLM_API_KEY:sk-xxx}  # 环境变量优先, 本地默认值回退
```

**关键设计**:
- `chat(systemPrompt, userPrompt)` → 返回纯文本
- `chatForJson(systemPrompt, userPrompt)` → 自动提取 ```json 块 → 解析为 JSONObject
- 超时 120s，温度 0.3（降低随机性，适合评估场景）

---

## 二、★ H3 LLM-as-Judge 打分

**文件**: `eval-application/.../strategy/LlmScoringStrategy.java`

### Prompt 设计

**系统提示词**（角色设定）:
```
你是一个企业级业务评估分析师。你会收到一个评估对象的多个指标数据。
请对每个指标独立打分（0-100 分），并给出简短理由。

评分规则：
  - 0-100 分，分数越高表示该指标表现越好
  - 将指标实际值与参考标准对比，考虑趋势和上下文
  - 不要机械地按区间映射，考虑指标的改善/恶化趋势

回复 MUST 是严格的 JSON 格式:
{ "scores": [{"indexCode": "xxx", "score": 85, "reason": "..."}], "overallComment": "..." }
```

**用户提示词**（数据注入）:
```
## 评估对象
- 对象ID: {bizId}

## 指标数据
| 指标编码 | 指标名称 | 实际值 |
|---------|---------|-------|
| COST_DEV | 费用偏差率 | 9.2 |
| ABNORM_CNT | 异常波动次数 | 2 |
| FILL_RATE | 填报及时率 | 85.5 |

请对以上 3 个指标逐一打分。
```

### 降级机制

```
LLM 调用
  ├─ 成功 → 解析 JSON → 返回 scores
  └─ 失败 (网络/超时/格式错误)
       └─ 降级: 所有指标默认 70 分, reason="LLM 不可用，默认 70 分"
```

### 双通道对比

```
同一组指标
  ├─ LLM 通道 → scores_llm
  └─ 规则引擎 → scores_rule
       └─ DualChannelScoringService.compare()
            ├─ diff < 5%   → TRIVIAL    (正常波动)
            ├─ 5% ≤ diff < 15% → NOTABLE (值得关注)
            └─ diff ≥ 15%  → SIGNIFICANT (需人工仲裁)
```

**对比结果存入 `eval_indicator_log`**: llm_score, rule_score, score_diff, diff_level, llm_reason

---

## 三、★ H4 LLM 事件/异常检测

**文件**: `eval-application/.../event/LlmEventDetector.java`

### Prompt 设计

```
你是一个企业级业务风险评估专家。请判断该对象是否存在数据异常、
恶意规避或需要人工关注的风险点。

返回 JSON:
{
  "hasAnomaly": true/false,
  "riskLevel": "NONE" / "LOW" / "HIGH",
  "description": "一句话描述风险情况",
  "redLineCandidates": ["可能触发的红线类型"]
}
```

### 双通道事件对比

```
规则引擎通道 (JEXL)              LLM 通道 (语义)
┌─────────────────────┐       ┌─────────────────────┐
│ dimension_rule 求值  │       │ Prompt → JSON       │
│ RED_LINE / BONUS    │       │ hasAnomaly / risk   │
│ DEDUCT / MARK       │       │ redLineCandidates   │
└────────┬────────────┘       └────────┬────────────┘
         │                             │
         └──────────┬──────────────────┘
              ┌─────▼─────┐
              │ 对比判定    │
              │ RULE / LLM │
              │ / BOTH     │ → triggerSource
              └─────┬─────┘
              ┌─────▼─────┐
              │ 红线机制    │
              │ blocked=true│
              │ adjScore*0.6│
              └───────────┘
```

**价值**: 
- 规则触发了但 LLM 认为正常 → 规则可能太严
- LLM 报了异常但规则没触发 → 规则有盲区
- 两者一致 → 高置信

---

## 四、★ H6 AI 总结（两轮对话）

**文件**: `eval-application/.../service/AiSummaryService.java`

### 两轮对话流程

```
Round 1: 生成
  输入: 指标得分 + 对比数据 + 事件 + 等级 + 排名
  Prompt: "你是一个企业级业务评估分析师，请生成评估总结"
  输出: { overall, strength, weakness, suggestion }

Round 2: 自审
  输入: Round1 的草稿 + 原始数据
  Prompt: "你是一个严格的审阅者，请检查：
           1. 有没有遗漏的异常信号？
           2. 措辞是否过分严厉/乐观？
           3. 数据引用是否准确？
           如果发现问题，请直接修改。"
  输出: { overall, strength, weakness, suggestion } (修正版)

最终总结:
  【整体】{overall}
  【优势】{strength}
  【问题】{weakness}
  【建议】{suggestion}
```

### 降级

```
LLM 调用
  ├─ 成功 → 两轮自审总结
  └─ 失败 → 模板化总结:
       "该评估对象综合得分XX分，表现[良好/一般/需关注]。
        持续关注指标变化趋势。"
       summaryStatus = "FALLBACK"
```

---

## 五、架构规则

### 分工铁律

```
LLM 做:  语义判断 (打分、异常检测、总结)
规则引擎做: 确定性运算 (聚合、排名、等级区间)
永不混淆: 不让 LLM 做数学，不让规则引擎做判断
```

### 树聚合——审计底线

```
LEAF 层: LLM 打分 → score
         ↑ LLM 到此为止
NORMAL层: 规则引擎 → weighted_sum / sum / min
TOP层:   规则引擎 → JEXL 路由匹配
         ↑ 往上全是数学，永不交给 LLM
```

### 对比数据——最值钱的资产

每次评估默认双通道并行。对比数据 `eval_indicator_log` 记录了每次 LLM vs 规则的分歧。这些是 LLM 工程化的第一手素材，比任何论文都有说服力。

---

## 六、文件索引

| 功能 | 文件 |
|------|------|
| LLM 客户端 | `eval-infrastructure/.../llm/LlmClient.java` |
| LLM 配置 | `eval-infrastructure/.../llm/LlmConfig.java` |
| LLM 属性 | `eval-infrastructure/.../llm/LlmProperties.java` |
| Prompt 模板 | `eval-infrastructure/.../llm/PromptTemplate.java` |
| H3 LLM 打分 | `eval-application/.../strategy/LlmScoringStrategy.java` |
| H3 双通道对比 | `eval-application/.../strategy/DualChannelScoringService.java` |
| H4 LLM 事件检测 | `eval-application/.../event/LlmEventDetector.java` |
| H6 AI 总结 | `eval-application/.../service/AiSummaryService.java` |
| 降级策略 | 各 Strategy 的 `degraded*()` / `fallback*()` 方法 |

## 七、API 端点

| 端点 | AI 功能 |
|------|---------|
| `POST /evaluation/execute` | H3 LLM 打分 + H4 LLM 事件检测 |
| `POST /evaluation/summary/{id}` | H6 AI 两轮总结 |
| `GET /evaluation/compare/stats` | 双通道对比统计 |
| `GET /evaluation/dashboard/{scene}` | 图表数据 (含 LLM vs 规则对比) |
