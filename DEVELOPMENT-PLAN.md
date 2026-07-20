# AI 评估系统 — 业余开发计划 v2

> **核心哲学**: AI 坐主桌，规则引擎当镜子。LLM 先打分，规则引擎并行跑来做对比。
> **模式**: 晚上 2-3 小时 / 周末半天 4 小时
> **总计**: 约 42 个 session，业余 ≈ 3 个月

---

## 执行状态

> 上次更新: 2026-07-20 | 当前: **S19 剩余表 DDL+Entity+Mapper**

### 里程碑概览

| 里程碑 | 内容 | Session | 状态 | 完成度 |
|--------|------|---------|------|--------|
| M0 | 地基 | S1-S2 | ✅ | 2/2 |
| M1 | ★ AI 先打分 | S3-S10 | ✅ | 8/8 |
| M2 | 规则引擎 + 对比 | S11-S18 | ✅ | 8/8 |
| M3 | 完整系统 | S19-S30 | 🔄 | 4/12 |
| M4 | AI 深化实验 | S31-S36 | ⬜ | 0/6 |
| M5 | 开源发布 | S37-S42 | ⬜ | 0/6 |

### 逐 Session 状态

| # | Session | 状态 | 完成日期 | 备注 |
|---|---------|------|---------|------|
| S1 | 开发环境搭建 | ✅ | 2026-07-20 | JDK 17, Maven 骨架, 编译通过 |
| S2 | 数据库就绪 | ✅ | 2026-07-20 | 25张表, V003 clean schema |
| S3 | eval-common 枚举+异常+统一响应 | ✅ | 2026-07-20 | 7个枚举 + Result + EvalException |
| S4 | 25张表 Entity+Mapper | ✅ | 2026-07-20 | 25 Entity + 25 Mapper 全部完成 |
| S5 | Pipeline 骨架 + Handler 接口 | ✅ | 2026-07-20 | Handler接口 + ConfigurablePipeline + EvaluationContext + PingHandler |
| S6 | LLM 客户端 + Prompt 基础设施 | ✅ | 2026-07-20 | LlmClient + LlmConfig + PromptTemplate |
| S7 | DataPull 路径A + H2 最简版 | ✅ | 2026-07-20 | DataPullService + FetchIndicatorValuesHandler + ADR-019维度补齐 |
| S8 | ★ H3 LLM-as-Judge 打分 | ✅ | 2026-07-20 | LlmScoringStrategy + LlmCalculateScoresHandler + 降级兜底70分 |
| S9 | H6 落库 + H1 最简版 | ✅ | 2026-07-20 | ValidateAndLoadModelHandler + SummarizeResultHandler |
| S10 | M1 验证 — 端到端链路 | ✅ | 2026-07-20 | DeepSeek 真实打分: totalScore=76.67, 3指标有据可查 |
| S11 | JEXL 表达式引擎封装 | ✅ | 2026-07-20 | ExpressionUtil + 变量预处理 |
| S12 | 规则引擎评分策略 | ✅ | 2026-07-20 | RuleScoreStrategy + 三级Fallback + 4种score_mode |
| S13 | ★ 对比引擎 DualChannel | ✅ | 2026-07-20 | DualChannelScoringService + 差异分级 TRIVIAL/NOTABLE/SIGNIFICANT |
| S14 | 对比数据持续积累 | ✅ | 2026-07-20 | 双通道并行 + stats API: SIG=66.7%, avgDiff=49.77 |
| S15 | Stage 树装配 + 树聚合 | ✅ | 2026-07-20 | StageNode+Assembler+TreeAggregator, 3单测全绿, 2级加权树:82分 |
| S16 | H3 top路由 (跳过派生指标) | ✅ | 2026-07-20 | TOP JEXL路由: attrValues["dept"]=="LOGISTICS" → 命中分支, 默认fallback |
| S17 | H4 事件/红线 双通道 | ✅ | 2026-07-20 | EventRuleEvaluator + LlmEventDetector + triggerSource: RULE/LLM/BOTH |
| S18 | M2 验证 — 双通道对比系统 | ✅ | 2026-07-20 | 39条对比: SIG=74.4%, avgDiff=45.94, 10次批量评估 |
| S19 | 剩余23张表 DDL+Entity+Mapper | ⬜ | - | |
| S20 | DataPull 路径B + MQ 批量异步 | ⬜ | - | |
| S21 | 深拷贝 + 模型配置 API | ⬜ | - | |
| S22 | 申诉体系 | ⬜ | - | |
| S23 | 等级映射 + 排名 + 回调 | ⬜ | - | |
| S24 | H6 AI 总结 多轮对话 | ✅ | 2026-07-20 | AiSummaryService 两轮自审, DeepSeek 280字总结 |
| S25 | 配置管理 + Caffeine 缓存 | ✅ | 2026-07-20 | ModelConfigCache @Cacheable TTL 5min, H1缓存+HIT, jar启动6s |
| S26 | 性能优化 | ⬜ | - | |
| S27 | 测试补充（上） | ⬜ | - | |
| S28 | 测试补充（下） | ⬜ | - | |
| S29 | Docker + 文档（上） | ⬜ | - | |
| S30 | Docker + 文档（下）+ M3 验证 | ⬜ | - | |
| S31 | 多模型对比 | ⬜ | - | |
| S32 | NL → JEXL 实验 | ⬜ | - | |
| S33 | RAG 历史案例检索 | ⬜ | - | |
| S34 | AI 实验记录台 | ⬜ | - | |
| S35 | M4 验证 + 数据积累（上） | ⬜ | - | |
| S36 | M4 验证 + 数据积累（下） | ⬜ | - | |
| S37 | Gitee 仓库准备 | ⬜ | - | |
| S38 | Demo 场景完整准备 | ⬜ | - | |
| S39 | 技术博客 | ⬜ | - | |
| S40 | 收尾 + 打磨（上） | ⬜ | - | |
| S41 | 收尾 + 打磨（下） | ⬜ | - | |
| S42 | 正式发布 v1.0.0 | ⬜ | - | |

> 状态图例: ⬜ 未开始 | 🔄 进行中 | ✅ 已完成 | ⏭️ 跳过

---

## 架构哲学

```
┌──────────────────────────────────────────────────┐
│              评估配置 (领域模型 — 不变)              │
│   模型 → Stage树 → 指标 → 参考标准                 │
│   (这套抽象是 AI 和规则引擎的共同底座)               │
├──────────────────────────────────────────────────┤
│            评分通道 (默认并行跑)                     │
│                                                   │
│   ┌─────────────┐          ┌─────────────┐       │
│   │  LLM 通道    │          │ 规则引擎通道  │       │
│   │ (默认)       │          │ (对比基线)    │       │
│   │ 语义判断     │    vs    │ JEXL 确定计算 │       │
│   │ 上下文感知   │          │ 可审计路径    │       │
│   └──────┬──────┘          └──────┬──────┘       │
│          │                        │               │
│          └───────────┬────────────┘               │
│                      │                            │
│              ┌───────▼────────┐                   │
│              │  对比 + 仲裁    │                   │
│              │  差异 > 阈值?   │                   │
│              └───────┬────────┘                   │
│                      │                            │
│              ┌───────▼────────┐                   │
│              │  树聚合 (规则引擎，永不替代) │       │
│              │  weighted_sum / sum / min  │       │
│              │  确定性的数学运算            │       │
│              └───────┬────────┘                   │
│                      │                            │
│              ┌───────▼────────┐                   │
│              │ 事件 + 等级 + 落库 │                 │
│              │ (LLM 异常检测 + 规则兜底) │          │
│              └────────────────┘                   │
└──────────────────────────────────────────────────┘

分工铁律:
  LLM 做: 语义判断 (打分、异常检测、总结)
  规则引擎做: 确定性运算 (聚合、排名、等级区间)
  永不混淆: 不让 LLM 做数学，不让规则引擎做判断
```

---

## AI 替代了什么？（一张表说清楚）

| Handler | AI 替代? | 为什么 |
|---------|---------|--------|
| H1 加载配置 | ❌ | 读数据库，AI 没价值 |
| H2 提取指标值 | ❌ | 字段映射，确定性操作 |
| **H3 标准匹配+打分** | **★ LLM-as-Judge (默认)** | 语义理解，上下文判断 |
| H3 派生指标 | ❌ | 数学运算，LLM 会算错 |
| **H3 树聚合** | **❌ 永不替代** | 加权求和必须确定，这是审计底线 |
| **H4 事件/红线** | **★ LLM 语义检测 (默认)** | 异常不靠穷举规则 |
| H5 申诉改分 | ❌ | 人工决策，系统只执行 |
| H6 等级映射 | ❌ | 分数→等级是查表 |
| **H6 总结** | **★ LLM 多轮对话** | 生成+自审 |
| Ranking | ❌ | 数学排序 |

**三个 AI 切入点，恰好是评估系统里最有判断力的三个环节。**

---

## 里程碑地图

```
M0: 空项目跑通                              [Session 1-2]
  │
M1: ★ AI 先打分                             [Session 3-10]   ← 核心差异化
  │   验证: LLM 对一个对象打出合理分数
  │
M2: 规则引擎 + 对比                          [Session 11-18]  ← 核心竞争力
  │   验证: LLM vs 规则 对比数据 ≥ 20 条
  │
M3: 完整系统                                 [Session 19-30]
  │   验证: 事件 + 派生 + 路由 + 批量异步 + 申诉
  │
M4: AI 深化 + 多模型                         [Session 31-36]  ← 实验田
  │   验证: 3 个模型对比数据 ≥ 50 条
  │
M5: 开源发布                                 [Session 37-42]
      验证: Gitee 仓库 + Demo + 博客
```

> 关键变化: AI 从 v1 旧计划的 M3（第 23 个 session）提前到 M1（第 8 个 session）。
> **你不是在评估系统上加 AI。你是用 AI 做评估，用规则引擎做验证。**

---

## Part 0: 地基（Session 1-2）

### S1: 开发环境搭建
**预计**: 2 小时

```
□ 安装 Eclipse Temurin 17
□ IDEA 新建 Maven 多模块项目
□ 父 POM: Spring Boot 3.3.x + MyBatis-Plus 3.5.x + JEXL 3.3 + Hutool 5.8
□ eval-boot 跑通
```

**验证**: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`

---

### S2: 数据库就绪 — 25 张表完整建库
**预计**: 2 小时 | **实际**: 本地 MySQL，基于生产 DDL

```
□ 复用本地 MySQL 8 (不搞 Docker)
□ 基于 poc-create-20260710.sql (21张) + 规则引擎 4张 → 25 张表
□ dr_ 前缀 → eval_
□ 去若依系统字段: domain_code, catagory_code, tenant_id, data_org_id,
  main_id, created_by, updated_by, enabled, is_active, 时区字段
□ 加 enabled TINYINT (逻辑删除), 保留 status (业务状态)
□ id VARCHAR(20) → BIGINT AUTO_INCREMENT
□ created_at/updated_at → create_time/update_time
□ 日志表统一: eval_task_log → eval_object_log → eval_indicator_log → eval_event_log
□ msg 表保留: 大字段分离优化
□ DDL: docs/sql/V003__clean_schema.sql
```

**验证**: 25 张表建好，应用连接正常，`/actuator/health` → UP

---

## Part 1: 领域骨架（Session 3-5）

### S3: eval-common — 枚举 + 异常 + 统一响应
**预计**: 2 小时 | **实际**: 部分已在 S1 完成

```
□ 枚举: StageType(TOP/NORMAL/LEAF), EventType, AggregateMode, ScoreMode, AppealType
□ ErrorCode 枚举 (S1 已完成)
□ EvalException(code, message) (S1 已完成)
□ Result<T>(code, message, data) (S1 已完成)
□ GlobalExceptionHandler (S1 已完成)
```

**验证**: `mvn compile -pl eval-common` → 零错误

---

### S4: 25 张表 Entity + Mapper
**预计**: 4 小时

```
□ eval-model 配置层 ×5: EvalModel, EvalModelStage, EvalModelIndex, EvalModelEvent, EvalModelStandard
□ eval-scene 方案层 ×3: EvalScene, EvalSceneStage, EvalSceneIndex
□ eval-index 指标层 ×3: EvalIndex, EvalIndexCatalog, EvalIndexStandard
□ eval-target ×1
□ eval-decision 规则引擎 ×4: EvalDecisionRule, EvalDecisionScene, EvalDimension, EvalSimpleRule
□ eval-task/object/indicator/event 日志层 ×6: 含 msg 表
□ eval-appeal 申诉 ×2
□ eval-grade-mapping ×1
□ 每个 Entity 配一个 MyBatis-Plus Mapper
□ V003 clean schema 对齐: create_time/update_time/enabled/status/id BIGINT
```

**验证**: `mvn test` → 所有 Mapper insert+selectById 全绿

---

### S5: Pipeline 骨架 + Handler 接口
**预计**: 3 小时

```
□ Handler 接口: stepCode(), stepName(), execute(ctx), shouldSkip(ctx), order()
□ EvaluationContext (定义全部字段)
□ ConfigurablePipeline: 注入 List<Handler> → 按 order 排序 → 顺序执行
□ PingHandler: 只打日志 "Pipeline is alive"
```

**验证**: 测试中 `pipeline.execute(ctx)` → 控制台打印

---

## Part 2: ★ AI 先坐主桌（Session 6-10）

> 这一段是整个项目最核心的部分。先让 LLM 能打分。
> 规则引擎后面再做——它的角色是对比基线，不是主逻辑。

### S6: LLM 客户端 + Prompt 基础设施
**预计**: 3 小时

```
□ LlmClient: 支持 OpenAI 兼容接口 (OpenAI / DeepSeek / 本地)
  - chat(String systemPrompt, String userPrompt) → String
  - chatWithJsonResponse(...) → JsonNode (结构化输出)
□ 配置: application.yml → llm.provider, llm.api-key, llm.base-url, llm.model
□ Prompt 模板: PromptTemplate.render(template, Map<String,Object> variables)
□ 单元测试: 调真实 LLM → 确认返回非空
```

**验证**: `llmClient.chat("你是什么模型？", "")` → 有合理回复

---

### S7: DataPull 路径A + H2 最简版
**预计**: 2 小时

```
□ DataPullService: 路径A（request.data 直传）
□ RawData: { bizId, fields: Map<String, Object> }
□ H2 FetchIndicatorValuesHandler (最简版):
  - 从 rawData.fields 按 indexCode 提取值 → rawValues
  - dimDefinitions 暂为空
```

**验证**: 传 `{"emply_id":"E001","logFillRate":85}` → rawValues = {logFillRate: 85}

---

### S8: ★ H3 — LLM-as-Judge 打分（核心！）
**预计**: 4 小时（周末半天）

```
□ 不做规则引擎。直接让 LLM 打分。

□ Prompt 设计 (关键):
  系统提示词:
    "你是一个企业级业务评估分析师。你会收到一个评估对象的多个指标数据，
    以及每个指标的参考标准。请对每个指标独立打分（0-100），并给出理由。
    回复 MUST 是严格的 JSON 格式。"

  用户提示词模板:
    """
    ## 评估对象
    - 对象ID: {bizId}
    - 数据周期: {dataPeriod}

    ## 指标数据
    {#each indicators}
    ### {indexName} ({indexCode})
    - 实际值: {rawValue}
    - 参考标准: {standardDescription}
    - 权重: {weight}
    {/each}

    请对以上 {indicatorCount} 个指标逐一打分。
    回复格式:
    {
      "scores": [
        {"indexCode": "...", "score": 85, "reason": "..."},
        ...
      ]
    }
    """

□ LlmScoringStrategy implements ScoreStrategy
  - 构建 Prompt → 调 LLM → 解析 JSON 回复
  - Fallback: LLM 调用失败 → 所有指标默认 70 分（并标记为 DEGRADED）

□ H3 CalculateScoresHandler (初版):
  - 不做树聚合。只有单层 stage，直接调 LlmScoringStrategy
  - 把 LLM 返回的 scores 写入 IndicatorResult[]
  - 简单总分 = 所有指标得分 × 权重 加权平均

□ 单元测试: 3 个指标 → LLM 返回 3 个分数 → 总分合理
```

**验证**: POST /api/v1/evaluation/execute → LLM 返回的 JSON 被正确解析 → totalScore 在 0-100 之间

---

### S9: H6 落库 + H1 最简版
**预计**: 3 小时

```
□ H1 ValidateAndLoadModelHandler (最简版):
  - 查 scene → model → stages → indices → indexBase
  - 不装配 Stage 树（先只支持单层）
  - 输出到 ctx

□ H6 SummarizeResultHandler (最简版):
  - 计算 totalScore（从 indicatorResults 聚合）
  - 落库: eval_log (1条) + eval_log_item (N条, clazz=INDEX)
  - 落库时记录 scoringMode = "LLM"  ← 关键！标记这是 AI 打的

□ Controller: POST /api/v1/evaluation/execute
  - 接收: { sceneCode, bizId, dataPeriod, data }
  - 返回: { bizId, totalScore, scoringMode, indicators: [{score, reason}] }
```

**验证**: curl POST → 返回 totalScore + 每个指标的 LLM 打分理由

---

### S10: M1 验证 — AI 先打分的端到端链路
**预计**: 2 小时

```
□ 从零开始:
  1. 执行 schema.sql (5张表)
  2. 手动插入 1 个 model + 3 个指标 + 参考标准描述
  3. 启动应用
  4. curl POST /api/v1/evaluation/execute { sceneCode, bizId, data }
  5. 返回: LLM 打出的分数 + 每个指标的理由

□ 准备一个 Demo 例子:
  - 场景: "物流费用合理性评估"
  - 3 个指标: 费用偏差率、异常波动次数、填报及时率
  - 真实的数据 + 真实的 LLM 判断 → 截图

□ Git tag: v0.1.0-m1-ai-first

□ 这一张截图，比你写 100 个 test case 都有说服力。
```

**验证**: LLM 打分结果看起来合理（肉眼判断），数据落库 scoringMode="LLM"

---

## Part 3: 规则引擎当镜子（Session 11-18）

> 现在 AI 能打分了。下一步不是"补充规则引擎"，
> 而是"让规则引擎并行跑一遍，跟 AI 对比"。
> 这个对比数据是你在 Gitee 上独一无二的资产。

### S11: JEXL 表达式引擎封装
**预计**: 2 小时

```
□ ExpressionUtil: JEXL 沙箱 + 6 种变量预处理
  - ${val}, ${weight}, ${attr.xxx}, ${dim.xxx}, ${idx.xxx.value}, ${idx.xxx.score}
□ JexlContext 构建: 把 rawValues + attrValues 注入
□ 单元测试: "${val} * ${weight}" → 正确求值
```

**验证**: 一个 JEXL 表达式 + 变量 → 返回正确计算结果

---

### S12: 规则引擎评分策略
**预计**: 3 小时

```
□ RuleScoreStrategy implements ScoreStrategy (与 LlmScoringStrategy 并列)
□ 标准匹配: 区间匹配 (min_value ≤ rawValue < max_value) → score
□ 字典匹配: dict_value 精确匹配
□ 表达式匹配: dimension_rule JEXL 条件求值
□ 三级 Fallback: 模型标准 → 通用标准 → 默认 100
□ score_mode: INTERVAL_WEIGHT / FIXED / RAW_WEIGHT
```

**验证**: 同一个指标 → RuleScoreStrategy 返回与 LLM 不同的分数 → 正常，这是对比的基础

---

### S13: ★ 对比引擎（这个系统最值钱的组件）
**预计**: 4 小时（周末半天）

```
□ DualChannelScoringService (双通道评分服务):
  1. 同一组指标数据
  2. LLM 通道跑一遍 → scores_llm
  3. 规则引擎跑一遍 → scores_rule
  4. 逐指标对比 → 计算差异

□ 对比结果结构:
  {
    "indexCode": "logFillRate",
    "indexName": "日志填报率",
    "rawValue": 85.5,
    "llmScore": 90,
    "llmReason": "填报率高于同区域均值，且近3个月持续改善",
    "ruleScore": 70,
    "ruleReason": "区间 [80,90) → 70分",
    "diff": 20,
    "diffLevel": "SIGNIFICANT"     // TRIVIAL(<5) / NOTABLE(<15) / SIGNIFICANT(≥15)
  }

□ 差异分级:
  - TRIVIAL (< 5 分): 正常波动
  - NOTABLE (5-15 分): 值得关注，AI 可能看到了规则看不到的东西
  - SIGNIFICANT (≥ 15 分): 需要人工仲裁 —— 要么规则太死，要么 AI 误判

□ 对比摘要: totalDiff, diffLevel, significantCount, notableCount

□ API: GET /api/v1/evaluation/compare/{logId}
  → 返回逐指标对比 + 差异分级
```

**验证**: 同一对象 → 两个 scoreMode 都跑完 → compare API 返回对比结果

---

### S14: 对比数据持续积累
**预计**: 2 小时

```
□ eval_log_item 扩展: llm_score, rule_score, score_diff, diff_level, llm_reason
□ 每次评估默认双通道并行
□ API: GET /api/v1/evaluation/compare/stats?sceneCode=xxx
  → 返回统计:
    { totalCompared, avgDiff, significantRate, topDiffIndicators, ... }
□ 这组统计数据是你以后写文章的硬素材
```

**验证**: 跑 10 次评估 → stats API 返回有意义的数据

---

### S15: Stage 树装配 + 树聚合（规则引擎负责）
**预计**: 3 小时

```
□ StageNodeAssembler.assemble(stages):
  - parentId 递归装配 → StageNode 树/森林
  - 支持 top/normal/leaf 三种类型

□ TreeAggregator (树聚合器):
  - 自底向上: 按 level 倒序遍历
  - leaf: 收集子指标得分 (来自 LLM 或规则引擎)
  - normal: 聚合子 stage 得分 (weighted_sum / sum / min)
  - top: 路由命中 → 只算该分支
  - 权重归一化

□ 关键设计:
  - 聚合永远由规则引擎负责（不管分数来源是 LLM 还是规则）
  - LLM 的输出在 leaf 层就停下了。往上全是数学。
  - 这是审计底线。不能让 LLM 做加法。

□ 单元测试: 2级树 → leaf得分 → normal聚合 → 分数正确
```

**验证**: 不管 leaf 层的分数是 LLM 打的还是规则引擎打的，聚合结果都对

---

### S16: H3 — top 路由 + 派生指标
**预计**: 3 小时

```
□ top 路由:
  - 根节点 type=top → 评估子分支路由条件 (JEXL)
  - 命中唯一分支 → 只算该分支
  - 默认分支配置

□ 派生指标:
  - calculateType=DERIVED → 按 layer 排序 → JEXL 求值
  - 结果写入 rawValues
  - 后续流程不变（基础指标和派生指标等价进入评分）

□ 注意: 派生指标的唯一计算者永远是规则引擎（JEXL）。
  不让 LLM 算 `${idx.A001.score} * 0.6 + ${idx.B002.score} * 0.4`
```

**验证**: top 路由到不同分支 → 不同分支 score 不同；派生指标 C = A×0.6+B×0.4 → 值正确

---

### S17: H4 — 事件/红线（双通道）
**预计**: 3 小时

```
□ 规则引擎通道 (JEXL):
  - 按 priority 排序评估事件
  - RED_LINE / BONUS / PENALTY / EVENT_SCORE

□ LLM 通道 (新增):
  - Prompt: 指标值 + 得分 + 趋势 + 同区域对比 → LLM 判断是否有异常
  - "该对象是否存在数据异常、恶意规避或需要人工关注的风险点？"
  - 回复 JSON: { hasAnomaly: bool, riskLevel: "NONE"/"LOW"/"HIGH", description: "..." }

□ 对比:
  - 规则引擎触发了 RED_LINE 但 LLM 认为 NONE → 可能规则太严
  - LLM 报了 HIGH 但规则引擎没触发 → 可能规则有盲区
  - 两者一致 → 高置信

□ 事件日志落 eval_event_log (标记触发来源: RULE / LLM / BOTH)
```

**验证**: 一个对象 → LLM 报异常 + 规则未触发 → 事件日志有 BOTH 标记

---

### S18: M2 验证 — 完整的双通道对比系统
**预计**: 2 小时

```
□ 端到端:
  LLM 打分 → 规则引擎打分 → 对比报告 → 树聚合(规则引擎) → 事件(双通道) → 落库

□ 对比数据积累 ≥ 20 条

□ 整理对比统计:
  - 哪些指标 LLM 和规则引擎分歧最大？
  - SIGNIFICANT 差异占比多少？
  - 有没有模式？

□ Git tag: v0.2.0-m2-dual-channel
```

**验证**: 对比统计 API 有 20+ 条数据，差异分布有意义

---

## Part 4: 完整系统（Session 19-30）

### S19: 剩余 23 张表 DDL + Entity + Mapper
**预计**: 3 小时

```
□ 全部 28 张表 DDL 执行
□ 剩余 Entity + Mapper (23 个 × 2)
□ 应用启动完整扫描通过
```

**验证**: 启动日志无 ERROR，所有 Mapper 可注入

---

### S20: DataPull 路径B + MQ 批量异步
**预计**: 4 小时（周末半天）

```
□ GroupViewDataPuller: viewCode 分组取数
□ EvalTaskProducer: Publisher Confirm
□ EvalTaskConsumer: 批量消费
□ bizIds > 6 → MQ 异步
□ 进度查询 API
□ EvalTaskTimeoutScanner
```

**验证**: 100 对象批量 → 异步完成 → 全部落库

---

### S21: 深拷贝 + 模型配置 API
**预计**: 3 小时

```
□ SceneCopyDomainService: 模板→方案 深拷贝
□ ModelController: CRUD + Stage 树管理
□ SceneController: 方案创建/发布
```

**验证**: 模型 → 深拷贝创建方案 → 方案独立可编辑

---

### S22: 申诉体系
**预计**: 3 小时

```
□ AppealDomainService: 提交/批量导入/执行重算
□ H5 AppealAdjustHandler: BONUS/PENALTY/TOTAL
□ AppealController API
```

**验证**: 提交申诉 → 执行重算 → 分数变化

---

### S23: 等级映射 + 排名 + 回调
**预计**: 3 小时

```
□ GradeMappingDomainService: SCORE_RANGE
□ RankingHandler: 奥运排名
□ CallbackNotifyService: 异步回调 + 模板
```

**验证**: totalScore=82 → grade="A" → rank=3

---

### S24: H6 — AI 总结（多轮对话）
**预计**: 3 小时

```
□ 旧系统: 单次 LLM 调用 → 总结

□ 新设计: 两轮对话
  Round 1: LLM 生成评估总结
    Prompt: 评估结果 + 各指标得分 + AI vs 规则对比 + 事件
    → Summary v1

  Round 2: LLM 自审
    Prompt: "以下是一份评估总结。请检查：
      1. 有没有遗漏的异常信号？
      2. 措辞是否过分严厉/乐观？
      3. 数据引用是否准确？
      如果是，请直接修改。"
    → Summary v2 (final)

□ AiSummaryProducer → MQ → AiSummaryConsumer
□ AiCallGuard: 连续5次失败熔断60s
□ Fallback: 模板化总结
□ 实验记录: eval_ai_experiment 表记录 token 消耗 + 耗时
```

**验证**: 评估完成 → summary 经过两轮对话 → 质量肉眼优于单轮

---

### S25-S26: 配置管理 + 缓存 + 性能
**预计**: 5 小时（两个 session）

```
□ Caffeine 本地缓存: scene→model→stages (TTL 5min)
□ vn 版本号 → 配置变更自动失效
□ 批量评估事务优化
□ lite 查询（排除大字段）
```

**验证**: 连续3次评估只查1次数据库；1000对象 < 30s

---

### S27-S28: 测试补充
**预计**: 5 小时（两个 session）

```
□ Pipeline 全链路集成测试 (TestContainers)
□ H3 树聚合单元测试
□ 双通道对比测试
□ 事件触发测试
□ LLM Mock 测试
```

**验证**: `mvn test` → 全部通过，覆盖率 > 60%

---

### S29-S30: Docker + 文档 + M3 验证
**预计**: 4 小时

```
□ Dockerfile + docker-compose.yml
□ SpringDoc OpenAPI
□ README 完善
□ M3 验证: docker compose up → 完整流程
□ Git tag: v0.3.0-m3-complete
```

---

## Part 5: AI 深化实验（Session 31-36）

> 系统完整了。现在在完整系统上做 AI 实验。

### S31: 多模型对比
**预计**: 3 小时

```
□ 支持同时配置 2-3 个 LLM (DeepSeek / GLM / Qwen / GPT)
□ 同一评估任务 → N 个模型各自打分
□ 对比: { indexCode, deepseekScore, glmScore, qwenScore, ruleScore }
□ 多模型一致性评分 (方差、Fleiss' Kappa)
```

**验证**: 3 个模型同时打分 → 对比表格有数据

---

### S32: NL → JEXL（实验功能）
**预计**: 3 小时

```
□ "入职天数小于30天的新员工，合格线是60分" → LLM 转 JEXL
□ 用户确认 → 保存为参考标准的 dimension_rule
□ API: POST /api/v1/rule/nl-to-jexl { naturalLanguage: "..." } → { jexl: "...", confidence: 0.9 }
□ 人工审核确认机制
```

**验证**: 输入自然语言 → 返回可执行的 JEXL

---

### S33: RAG 历史案例检索（实验）
**预计**: 3 小时

```
□ 历史评估记录 → Embedding → 向量存储 (ChromaDB / 本地文件)
□ 评估时检索 Top-3 相似案例
□ 注入 LLM Prompt 作为 few-shot 示例
□ API: GET /api/v1/evaluation/{logId}/similar → 相似历史案例
```

**验证**: 评估一个对象 → 查到 3 个相似历史案例

---

### S34: AI 实验记录台
**预计**: 2 小时

```
□ eval_ai_experiment 表
□ 每次 AI 调用记录: model, promptVersion, tokenUsed, durationMs, score, diffFromRule
□ API: GET /api/v1/ai-experiments/stats → 实验统计
□ 你的个人 AI 工程化日志
```

**验证**: 跑 10 次评估 → 10 条实验记录 → stats API 返回统计

---

### S35-S36: M4 验证 + 数据积累
**预计**: 4 小时

```
□ 跑 50 次评估（手动编不同场景的数据）
□ 对比数据 ≥ 50 条
□ 多模型实验数据 ≥ 30 条
□ 整理发现:
  - LLM vs 规则引擎差异最大的指标类型
  - 不同 LLM 的打分一致性
  - 哪些场景 LLM 明显优于规则
□ Git tag: v0.4.0-m4-ai-deep
```

---

## Part 6: 开源发布（Session 37-42）

### S37: Gitee 仓库准备
**预计**: 2 小时
```
□ LICENSE (Apache 2.0)
□ README: 定位 → "AI 原生评估系统：LLM 打分 + 规则引擎验证"
□ 架构图 (ASCII/图片)
□ CHANGELOG.md
```

---

### S38: Demo 场景完整准备
**预计**: 3 小时
```
□ 种子数据: 1个完整模型 + 2个方案 + 50个对象
□ Demo 脚本: 从头到尾的操作流程
□ 核心截图:
  - LLM 打分结果 + 理由
  - LLM vs 规则引擎对比表（SIGNIFICANT 差异高亮）
  - 多模型对比雷达图
  - AI 总结（两轮对话版）
```

---

### S39: 技术博客
**预计**: 3 小时
```
□ 标题: "LLM 打分 vs 规则引擎：一个 Java 评估系统的 AI 化实践"
□ 核心内容:
  1. 为什么"AI 坐主桌，规则引擎当镜子"
  2. Pipeline 如何变成双通道
  3. 50 条对比数据的分析
  4. 哪些指标 AI 比规则好，哪些不如
  5. 教训和下一步
□ 发布: Gitee README + 掘金 + 知乎
```

---

### S40-S41: 收尾 + 打磨
**预计**: 4 小时
```
□ 补充 JavaDoc
□ .gitignore, .editorconfig
□ CI/CD (GitHub Actions / Gitee Go)
□ 测试覆盖率补充
```

---

### S42: 正式发布
**预计**: 2 小时
```
□ Git tag: v1.0.0
□ Gitee/GitHub 推送
□ 发布公告
```

---

## 时间估算

| Part | 内容 | Session 数 | 业余 ≈ |
|------|------|-----------|--------|
| 0 | 地基 | 2 | 0.5 周 |
| 1 | 领域骨架 | 3 | 1 周 |
| 2 | ★ AI 先打分 | 5 | 1.5 周 |
| 3 | 规则引擎 + 对比 | 8 | 2 周 |
| 4 | 完整系统 | 12 | 3 周 |
| 5 | AI 深化实验 | 6 | 1.5 周 |
| 6 | 开源发布 | 6 | 1.5 周 |
| **总计** | | **42** | **~11 周 ≈ 3 个月** |

---

## 关键提醒

1. **AI 在第 8 个 session 就能跑**。不是第 23 个。这是 v2 计划最重要的改变。

2. **对比数据是你最值钱的资产**。S13 的 DualChannelScoringService 不是"附加功能"，它是整个系统的灵魂。

3. **树聚合永不交给 LLM**。这是审计底线。LLM 把 leaf 层的分打完就停下。

4. **每步验证**。不攒代码。每个 session 结束必须 git commit。

5. **Demo 截图就是你的技术品牌**。S38 的 LLM vs 规则对比表 + 多模型雷达图 → 比目录结构和测试覆盖率有说服力一万倍。
