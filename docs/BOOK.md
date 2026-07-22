# AI 评估系统 — 全书

> **定位**: 系统级技术手册，从概念到实现的全链路文档。
> **版本**: v1.1 | **日期**: 2026-07-22

---

# 第一部分：概念与架构

## 第一章：系统定位

### 1.1 解决什么问题

企业级业务评估（商机点检、合同评审、物流费用审计）有三个核心痛点：

| 问题 | 表现 | 本系统的解法 |
|------|------|------------|
| 评估标准不统一 | 不同评审人标准不同 | Model 模板 + Scene/方案实例化，标准可配置 |
| 指标体系分散 | 指标散落在代码/Excel | Index 统一注册，Stage 树组织评估逻辑 |
| 流程不可扩展 | 无法承载复杂规则 | Pipeline 链式处理 + Handler 独立扩展 |

### 1.2 核心哲学

> **AI 坐主桌，规则引擎当镜子。** LLM 先打分，规则引擎并行跑来做对比。

三个 AI 切入点，恰好是评估系统里最有判断力的三个环节：

| 环节 | AI 做什么 | 规则引擎做什么 |
|------|---------|-------------|
| H3 打分 | LLM 语义理解 + 自然语言理由 | JEXL 区间匹配（对比基线） |
| H4 事件检测 | LLM 判断异常/红线风险 | JEXL 条件规则（互补验证） |
| H6 总结 | LLM 两轮生成+自审 | 模板降级 |

---

## 第二章：核心概念模型

### 2.1 三层模型

**Stage 是系统的核心组织单元。**

```
┌──────────────────────────────────────────┐
│  模型（Model）                            │
│  评估模板：定义 Stage 树 + 指标池 + 事件  │
│  通过 SceneCopy 深拷贝创建方案             │
├──────────────────────────────────────────┤
│  方案/场景（Scene）  ← 同一个概念          │
│  Model 的实例化副本 = 可执行的评估方案     │
│  携带独立 Stage 树副本                    │
├──────────────────────────────────────────┤
│  ★ Stage（评估阶段/维度树） ← 系统核心     │
│  TOP    根节点 — JEXL 路由匹配             │
│  NORMAL 中间节点 — 加权聚合                │
│  LEAF   叶子节点 — 挂指标, LLM打分终端     │
│                                            │
│  整个评估计算 = Stage 树的自底向上遍历     │
└──────────────────────────────────────────┘
```

### 2.2 关键认知

- **Scene = 方案**。DB 里叫 `eval_scene`，API 里叫"方案"，代码里叫 Scene，三个名字指同一概念。
- **权重在 Stage 上，不在指标上**。同一个 Stage 下的指标等权，不同 Stage 按 weight 加权。
- **LLM 在 LEAF 层停下**。往上全是规则引擎数学——审计底线。

---

## 第三章：Pipeline 架构

### 3.1 处理链

```
H1 加载配置 → H2 提取指标 → H3 双通道打分+树聚合 → H4 事件检测 → H6 汇总落库
```

### 3.2 设计模式

| 元素 | 职责 | 模式 |
|------|------|------|
| Handler | 单个处理步骤，接收 Context，输出写回 Context | 职责链 |
| ConfigurablePipeline | 按 order 排序，顺序执行 Handler 链 | 管道模式 |
| EvaluationContext | 贯穿全链路的数据总线 | 上下文对象 |

### 3.3 Handler 清单

| Handler | 步 | order | 核心职责 |
|---------|-----|-------|---------|
| ValidateAndLoadModelHandler | H1 | 1 | 加载 scene→model→stages→indices (含缓存) |
| FetchIndicatorValuesHandler | H2 | 2 | 从 rawData 提取指标值 + 维度属性 |
| LlmCalculateScoresHandler | H3 | 3 | 双通道打分 + Stage 树聚合 |
| EventRedLineHandler | H4 | 4 | 双通道事件/红线检测 |
| SummarizeResultHandler | H6 | 6 | 等级映射 + 落库 (task/object/indicator log) |

---

# 第二部分：核心子系统

## 第四章：Stage 树

### 4.1 三种节点类型

| 类型 | 职责 | 例子 |
|------|------|------|
| TOP | 路由匹配，命中唯一子分支 | COST → 根据部门路由到不同评估策略 |
| NORMAL | 聚合子节点得分 | 加权求和/直接求和/取最小值 |
| LEAF | 挂载指标，收集 LLM/规则打分 | COST_EFFICIENCY → [费用偏差率, 异常次数] |

### 4.2 树聚合算法

```
自底向上遍历（按 level 降序）：
  1. LEAF 层: aggregateLeaf() — 从指标得分计算 leaf 得分
     支持 cap/floor 钳制，weighted_sum/sum/min 聚合
  2. NORMAL 层: aggregateNormal() — 加权聚合子节点得分
  3. TOP 层: aggregateTop() — JEXL 路由匹配，命中唯一分支
     未命中 → default fallback (第一个无条件子节点)
```

### 4.3 关键决策 (ADR-004, ADR-016)

- **Handler 顺序写死列表** (ADR-004): 不用 @Order 注解，显式 List 传递。理由：顺序即业务逻辑，隐藏的排序是 Bug 温床。
- **路由是 Stage 级决策** (ADR-016 相关): 不在指标层做路由——路由改变的是评估策略（选哪棵树），不是数据取值。

---

## 第五章：评分系统

### 5.1 双通道架构

```
同一组指标数据
  ├─ LLM 通道 → LlmScoringStrategy.scoreAll()
  │     └─ DeepSeek API → 语义打分 + 自然语言理由
  └─ 规则引擎 → RuleScoreStrategy.scoreAll()
        └─ JEXL + 区间匹配 + 三级 Fallback
             │
        DualChannelScoringService.compare()
             │
        ┌────┴────┐
        │ diff < 5  → TRIVIAL       正常波动
        │ 5≤diff<15 → NOTABLE       值得关注
        │ diff ≥ 15 → SIGNIFICANT   需人工仲裁
        └─────────┘
```

### 5.2 LLM Prompt 设计

当前版本：v3-standards-fewshot

**系统提示词**:
```
你是一个企业级业务评估分析师。
严格参考提供的评分标准区间来打分。
回复 MUST 是严格的 JSON: { "scores": [{...}] }
```

**用户提示词**:
```
## 历史参考案例 (AI与规则引擎一致的高质量案例)
案例1: FILL_RATE=85.5分, ...
[来自 eval_indicator_log WHERE diff_level='TRIVIAL' LIMIT 3]

## 当前评估对象
对象ID: xxx

## 指标数据与评分标准
| 指标编码 | 名称 | 实际值 | 评分标准 (min≤值<max→得分) |
[来自 eval_model_standard, 按 indexId+priority 排序]

请对以上 N 个指标逐一打分。
```

### 5.3 降级策略

```
LLM 调用
  ├─ 成功 → JSON 解析 → ScoreResult[]
  └─ 失败 → degradedScores()
       └─ 每个指标返回 70 分 + "LLM 不可用，默认 70 分"
```

**为什么是 70 分**: 不功不过的中性值——不会因 LLM 不可用导致错误的评估结论（50 太负面，90 太乐观）。

### 5.4 关键决策 (ADR-013, ADR-018)

- **表达式引擎复用 JEXL** (ADR-013): 变量预处理 (`${val}`, `${attr.xxx}`, `${idx.xxx.value}`)，沙箱安全。
- **score_mode 显式指定** (ADR-018): RAW_WEIGHT/FIXED/FIXED_WEIGHT/INTERVAL_WEIGHT 四种模式。

---

## 第六章：事件/红线系统

### 6.1 双通道事件检测

```
规则引擎通道                    LLM 通道
EventRuleEvaluator              LlmEventDetector
JEXL 条件评估                   Prompt 异常检测
RED_LINE / BONUS / DEDUCT      hasAnomaly / riskLevel
         │                             │
         └──────────┬──────────────────┘
              ┌─────▼──────┐
              │ 交叉对比    │
              │ RULE / LLM │
              │ / BOTH     │ → triggerSource
              └─────┬──────┘
              ┌─────▼──────┐
              │ 红线判定    │
              │ blocked=true│
              │ adjScore×0.6│
              └────────────┘
```

### 6.2 事件类型

| 类型 | 含义 | 触发方式 |
|------|------|---------|
| RED_LINE | 一票否决 | JEXL 条件或 LLM 检测 |
| BONUS | 加分 | JEXL 条件 |
| DEDUCT | 扣分 | JEXL 条件 |
| MARK | 标记提醒 | JEXL 条件 |

### 6.3 关键决策 (ADR-005)

- **红线检测内嵌于 Handler** (ADR-005): 不拆分为独立 Handler——事件检测和打分在同一个流程节点，避免数据不一致窗口。

---

## 第七章：等级映射与排名

### 7.1 等级映射

SCORE_RANGE 模式：分数区间 → S/A/B/C/D 等级。

```
eval_grade_mapping:
  sceneId | grade | lowerBound | upperBound | priority
     1    |   S   |     90     |    101     |    1
     1    |   A   |     80     |     90     |    2
     1    |   B   |     70     |     80     |    3
     ...
```

### 7.2 奥运排名 (ADR-016)

```
同分并列: 1,1,3,4,... (不是 1,1,2,3)
rank_total 写入总参评数

实现: 按总分降序排序 → 分数变化时 rank=i+1, 相同时 rank 不变
```

---

## 第八章：深拷贝与方案管理

### 8.1 深拷贝流程

```
Model (模板)                    Scene (方案)
┌──────────────┐               ┌──────────────┐
│ eval_model   │ ──copy──→     │ eval_scene   │
│ model_stage  │ ──copy+重映射→│ scene_stage  │  parentId 从旧ID→新ID
│ model_index  │ ──copy+重映射→│ scene_index  │  stageId 从旧ID→新ID
└──────────────┘               └──────────────┘
```

### 8.2 关键设计

- **Model 是模板，Scene 是实例**。SceneCopyDomainService 执行深拷贝。
- **parentId/stageId 重映射**: 先 INSERT 再 batch UPDATE——两次遍历，避免递归锁。
- **Scene 独立修改不影响 Model**: "改模板影响历史评估"是反模式。

---

## 第九章：申诉体系

### 9.1 流程

```
PENDING (提交) → APPROVED (审批) → 自动重算 adjustedTotalScore
                → REJECTED (驳回)
```

### 9.2 重算逻辑

- 原始 totalScore + scoreAdjustment = adjustedScore
- 范围保护: 0-100
- 直接更新 eval_object_log.appeal_adjusted_score

---

## 第十章：LLM 基础设施

### 10.1 架构

```
┌──────────────┐     OpenAI 兼容 API      ┌─────────────────┐
│  LlmClient   │ ───────────────────────→ │  DeepSeek        │
│              │  POST /v1/chat/completions│  / OpenAI / 本地 │
│  chat()      │ ←─────────────────────── │                  │
│  chatForJson()│     LlmResponse         └─────────────────┘
└──────────────┘
```

### 10.2 LlmResponse 可观测性

```java
public record LlmResponse(
    String content,      // 文本回复
    int inputTokens,     // 输入 token 数 (来自 API usage.prompt_tokens)
    int outputTokens,    // 输出 token 数 (来自 API usage.completion_tokens)
    long durationMs,     // 调用耗时 (毫秒)
    String errorType     // null=成功, "HTTP_429"=限流, "Timeout"=超时
) {}
```

### 10.5 Prompt 版本化管理 (A1.2)

**核心认知**: Prompt 是代码——可测试、可版本化、可对比、可回滚。

```
硬编码 (旧)                          DB 版本化 (新)
SYSTEM_PROMPT = "..."               SELECT template_text 
                                     FROM eval_prompt_template
                                     WHERE prompt_key=? AND is_active=1
```

**存储**: `eval_prompt_template` 表，每个 `(prompt_key, version)` 唯一一行。
**切换**: `POST /prompts/{id}/activate` — 激活新版本，自动禁用同 key 其他版本。
**对比**: `GET /prompts/stats` — 按版本聚合调用次数/延迟/token/错误率。

当前种子数据：v1-base / v2-standards / v3-fewshot，共 6 条（2 keys × 3 versions）。

### 10.3 实验记录 (A2)

每次 LLM 调用自动写入 `eval_ai_experiment`:

| 字段 | 来源 |
|------|------|
| experiment_type | SCORING / EVENT / SUMMARY |
| model | llmClient.getModel() |
| prompt_version | "v3-standards-fewshot" |
| input_tokens / output_tokens | API usage |
| duration_ms | System.currentTimeMillis() - start |
| temperature | llmClient.getTemperature() |
| error_type | null=成功 |

### 10.4 关键决策 (ADR-011, ADR-020)

- **AI 总结 Handler 必须编写** (ADR-011): MVP 阶段逻辑放空，但 Handler 占位保证架构完整。
- **AI总结复用 ConnectionFactory** (ADR-020): 不建独立连接池——评估任务和 AI 总结共享 LLM 连接资源。

### 10.6 RAG 向量检索 (A3)

**核心认知**: RAG 是一整条 pipeline——embedding 选型 → chunk 策略 → 向量化存储 → 检索 → 注入 prompt → 质量评测。

#### 技术选型

| 组件 | 选择 | 理由 |
|------|------|------|
| Embedding 模型 | bge-small-zh-v1.5 (BAAI, 512维) | 中文优化、本地 ONNX 推理 (90MB)、零 API 费用 |
| 向量存储 | Lucene 9.x KnnVectorField (HNSW) | 纯 Java、零外部依赖、Windows 兼容 |
| 推理引擎 | ONNX Runtime Java API | 直连推理，3-5ms encode |

#### Pipeline 流程

```
评估请求
  ├─ EmbeddingService.encode(指标名+实际值+理由) → 512维向量
  ├─ VectorIndexService.search(vector, K=5) → 相似案例 (6-11ms)
  ├─ 向量无结果 → SimilarCaseService 规则检索降级
  └─ 注入 LlmScoringStrategy.buildFewShot() → Prompt 的"历史参考案例"段
```

#### 检索质量评测 (A3.3，2026-07-22)

**数据集**: 168 条历史日志，15 条标注查询 (10 代表性 + 5 边界)，二元相关标注。

| 指标 | 通道 | K=1 | K=3 | K=5 |
|------|------|-----|-----|-----|
| Hit Rate@K | 向量 | 20.0% | 33.3% | 53.3% |
| | 规则 | 26.7% | 26.7% | 26.7% |
| NDCG@K | 向量 | 100% | 95.0% | 82.8% |
| | 规则 | 100% | 98.9% | 98.7% |

**结论**: 向量检索适合高召回、跨指标场景（HR@5 翻倍），规则检索适合精确匹配、排序稳定场景。详细实验笔记见 `wiki/research/RAG-检索质量评测-20260722.md`。

### 10.7 AI 可靠性工程 (A4)

#### 多模型 Fallback 链

```
DeepSeek (主) → GLM (备1) → Qwen (备2) → 降级默认 70 分
     │              │            │              │
     └─ 超时 120s ──┘─ 超时 60s ──┘─ 超时 60s ──┘
```

**实现**: `ResilientLlmClient` 封装 fallback 逻辑，链顺序通过 `application.yml` 配置。

#### 降级策略分层

| 级别 | 触发条件 | 行为 |
|------|---------|------|
| Level 1 | 主模型超时/限流 | 自动切备选模型 |
| Level 2 | 全部模型不可用 | 用规则引擎分数替代 LLM 分数 |
| Level 3 | 规则引擎也无结果 | 返回默认 70 分 + DEGRADED 标记 |

#### 熔断机制

- `AiCallGuard`: 连续 5 次失败 → 熔断 60s → 半开探测 → 恢复或继续熔断
- API: `GET /api/v1/evaluation/resilience` — 实时查询韧性状态

#### 关键设计

- `LlmProperties` / `LlmConfig`: 配置结构化——超时、重试、熔断阈值统一管理
- `ResilientLlmClientTest`: 6 用例覆盖正常/超时/fallback/熔断路径

---

# 第三部分：数据模型

## 第十一章：核心表结构

### 配置层

| 表 | 说明 | 关键字段 |
|----|------|---------|
| eval_model | 评估模型模板 | code, name, aggregate_mode |
| eval_model_stage | Stage 树节点 (模板层) | model_id, parent_id, type, weight, route_condition |
| eval_model_index | 指标-Stage 关联 (模板层) | model_id, stage_id, index_id |
| eval_model_standard | 评分标准区间 | model_id, index_id, min_value, max_value, score, priority |
| eval_model_event | 事件配置 | model_id, event_type, dimension_rule, priority |
| eval_index | 指标定义 | code, name, dimensions |

### 方案层 (SceneCopy 深拷贝产物)

| 表 | 说明 | 关键字段 |
|----|------|---------|
| eval_scene | 方案 | code, model_id, status (DRAFT/PUBLISHED) |
| eval_scene_stage | Stage 树 (方案层) | scene_id, parent_id, source_id |
| eval_scene_index | 指标关联 (方案层) | scene_id, stage_id, index_code, source_id |

### 运行时

| 表 | 说明 | 关键字段 |
|----|------|---------|
| eval_task_log | 评估任务日志 | code, scene_code, status |
| eval_object_log | 评估对象日志 | task_log_id, total_score, grade, eval_rank, summary |
| eval_indicator_log | 指标评分日志 | object_log_id, llm_score, rule_score, score_diff, diff_level |
| eval_event_log | 事件日志 | object_log_id, event_type, trigger_source (RULE/LLM/BOTH) |
| eval_appeal_header | 申诉头 | appeal_no, appeal_type, score_adjustment, status |
| eval_ai_experiment | AI 实验记录 | experiment_type, model, prompt_version, tokens, duration_ms |

---

# 第四部分：API 参考

## 第十二章：REST API

### 评估执行

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/evaluation/execute` | POST | 执行单对象评估 (双通道) |
| `/api/v1/evaluation/compare/stats` | GET | 双通道对比统计 |
| `/api/v1/evaluation/compare-models` | POST | 多模型对比 (S31) |
| `/api/v1/evaluation/rank/{sceneCode}` | POST | 奥运排名 |
| `/api/v1/evaluation/summary/{id}` | POST/GET | AI 总结 (两轮自审) |
| `/api/v1/evaluation/dashboard/{sceneCode}` | GET | Dashboard 图表数据 |
| `/api/v1/evaluation/experiments/stats` | GET | AI 实验统计 (A2) |

### 方案管理

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/scene/copy` | POST | 从模型创建方案 (深拷贝) |
| `/api/v1/scene/list` | GET | 方案列表 |
| `/api/v1/scene/{id}/publish` | POST | 发布方案 |

### 等级映射

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/grade-mapping/list` | GET | 查询等级配置 |
| `/api/v1/grade-mapping/batch` | POST | 批量保存 |

### 申诉

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/appeal/submit` | POST | 提交申诉 |
| `/api/v1/appeal/{id}/approve` | POST | 审批 + 重算 |
| `/api/v1/appeal/list` | GET | 申诉列表 |

---

# 第五部分：开发指南

## 第十三章：分层架构 (AGENTS.md 规范)

```
Controller        ← 参数校验 + 调用 DomainService / Service
  ↓
DomainService     ← 复杂业务编排: Pipeline 执行、深拷贝、申诉重算
  ↓
Service           ← 单表 CRUD: 不包含 if/else 业务判断
  ↓
Mapper            ← 数据访问: MyBatis-Plus BaseMapper
```

### 调用规则

- Controller → DomainService ✅
- Controller → Service ✅ (简单 CRUD)
- Controller → Mapper ❌
- DomainService → Mapper ✅ (Pipeline Handler 里可直接调)
- Service → DomainService ❌
- Service → Service ❌

### 任务完成 SOP

```
每个 Session 完成后:
  ① 更新 DEVELOPEMENT-PLAN.md + PLAN-DETAIL.md
  ② git commit (S{N}: {描述})
  ③ git push
```

---

## 第十四章：技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 17 (Temurin) | 运行环境 |
| Spring Boot | 3.3.5 | 应用框架 |
| MyBatis-Plus | 3.5.7 | ORM |
| MySQL | 8.0 | 数据库 |
| JEXL | 3.3 | 表达式引擎 |
| Caffeine | (via Spring Cache) | 本地缓存 TTL 5min |
| DeepSeek API | deepseek-chat | LLM 评分/检测/总结 |
| ONNX Runtime | 1.18 (Java API) | Embedding 本地推理 (A3) |
| Lucene | 9.x (KnnVectorField) | 向量索引 HNSW (A3) |
| OkHttp | 4.12 | HTTP 客户端 (LlmClient) |
| Chart.js | 4.4 CDN | Dashboard 图表 |

---

## 第十五章：ADR 索引

全部 21 条架构决策记录位于 `docs/design/adr/`。

| 编号 | 标题 | 影响范围 |
|------|------|---------|
| ADR-001 | 统一评估接口，自动判断同步/异步 | API 设计 |
| ADR-002 | 管道不感知批量，循环调度在 Controller | Pipeline |
| ADR-003 | 采用固定线性管道，不引入 DAG 编排 | 架构 |
| ADR-004 | Handler 顺序显式写死列表，不用 @Order | 编码规范 |
| ADR-005 | 红线检测内嵌于 Handler，不拆分 | Pipeline |
| ADR-006 | DATA-PULL 只在 Controller 执行一次 | 数据流 |
| ADR-009 | 聚合模式多级 Fallback (Stage→Model→默认) | 评分 |
| ADR-013 | 表达式引擎复用 JEXL，变量预处理 | 技术选择 |
| ADR-016 | 奥运排名方式处理同分并列 | 排名 |
| ADR-018 | score_mode 字段显式指定得分计算方式 | 评分 |
| ADR-019 | 扩大 attrValues，dimDefinitions 作为翻译层 | 数据模型 |
| ADR-020 | AI总结复用 ConnectionFactory | AI 基础设施 |

*(完整 21 条见 `docs/design/adr/INDEX.md`)*

---

## 第十六章：实现注意事项

### LLM 调用

1. **temperature=0.3**：评估需要一致性，不是创意。低温度降低随机性。
2. **超时 120s**：DeepSeek 通常 2-5 秒，120s 覆盖网络抖动。
3. **JSON 提取容错**：LLM 可能用 ` ```json ` 包裹，需要预处理。
4. **降级永不抛异常**：LLM 不可用时返回 70 分，不阻塞 Pipeline。

### Stage 树

1. **父节点先于子节点创建**：按 level 升序遍历保证 `nodeMap.get(parentId)` 总能找到。
2. **类型自动判定**：无子节点 + 有指标 → 自动标记 LEAF。
3. **TOP 路由大小写敏感**：`"TOP".equals(type)` —— 数据库存大写。

### 双通道对比

1. **对比数据是最值钱的资产**：每次评估默认双通道并行，差异数据自动积累。
2. **规则引擎无标准时返回 rawValue**：这是"规则盲区"的信号，不是 Bug。

### 编码

1. **`-parameters` 编译标志**：`@PathVariable` 需要参数名，加在父 POM compiler plugin。
2. **测试用简化构造器**：`LlmScoringStrategy(LlmClient)` 不带 Mapper，避免 NullPointerException。
3. **JDK 17 路径**：Windows 环境可能有多个 JDK，`restart.sh` 显式指定 Temurin 17。

---

# 附录

## A. 文件索引

| 类别 | 位置 |
|------|------|
| 系统全书 | `docs/BOOK.md` (本文档) — 16章 + RAG/可靠性 |
| AI 功能实现 | `docs/AI-IMPLEMENTATION.md` |
| LLM 打分设计 | `docs/design/LLM-SCORING-DESIGN.md` |
| 核心架构 | `docs/design/ai-evaluation-system-architecture.md` |
| 术语表 | `docs/design/GLOSSARY.md` |
| 技术方案 | `docs/design/TECHNICAL-PLAN.md` |
| 架构决策 | `docs/design/adr/ADR-001~021` |
| 编码规范 | `AGENTS.md` |
| 开发计划 | `DEVELOPMENT-PLAN.md` |
| 执行明细 | `PLAN-DETAIL.md` |
| SQL 迁移 | `docs/sql/V001~V008` |
| 前端 Dashboard | `eval-boot/src/main/resources/static/index.html` |
| RAG 检索评测 | `wiki/research/RAG-检索质量评测-20260722.md` |
| RAG 基础知识 | `wiki/ai/RAG-基础知识-从零到懂.md` |
| RAG 落地指南 | `wiki/ai/RAG-落地实践指南.md` |

## B. 版本历史

| 日期 | 版本 | 内容 |
|------|------|------|
| 2026-07-20 | v0.1.0-m1 | M1: LLM 先打分 |
| 2026-07-20 | v0.2.0-m2 | M2: 双通道对比系统 |
| 2026-07-20 | v0.3.0-m3 | M3: 完整系统（Stage树/事件/排名/总结/方案管理） |
| 2026-07-21 | — | A1.2: Prompt 版本化管理 (v1-base/v2-standards/v3-fewshot) |
| 2026-07-21 | — | A2: LLM 可观测性 (eval_ai_experiment 全链路追踪) |
| 2026-07-21 | — | A3.1/A3.2: RAG 向量检索 + few-shot 注入 (bge-small + Lucene HNSW) |
| 2026-07-21 | — | A4: AI 可靠性工程 (ResilientLlmClient 熔断/重试/fallback) |
| 2026-07-21 | v1.0.0 | 正式发布 (README + CHANGELOG + Docker + restart.sh) |
| 2026-07-22 | — | A3.3: RAG 检索质量评测 (15条标注，向量 vs 规则 HR@K + NDCG@K) |
| 2026-07-22 | v1.1 | BOOK.md v1.1: 新增第10章 RAG + 可靠性, 技术栈更新 |
