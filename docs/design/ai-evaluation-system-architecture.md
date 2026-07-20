# AI 评估系统核心架构设计

> **文档定位**: 纯技术设计文档，面向有后端开发经验的读者
> **关注点**: 架构决策、设计模式、技术选型、演进路径
> **版本**: v2.0 | **日期**: 2026-07-20 | **作者**: accontras
> **v2.0 变更**: 重构概念模型——Stage 提升为核心角色，指标/属性降为辅助

---

## 一、背景与问题

### 1.1 业务背景

企业级业务评估场景（商机点检、合同评审、物流费用审计等）长期依赖人工经验，存在三个核心痛点：

| 问题 | 表现 | 影响 |
| :--- | :--- | :--- |
| **评估标准不统一** | 不同评审人对同一业务对象的判断标准不同 | 评估结果一致性差，决策质量不稳定 |
| **指标体系分散** | 指标定义、分类、参考标准散落在代码或 Excel 中 | 不可复用、不可追溯、维护成本高 |
| **评估流程不可扩展** | 一次评估涉及多维度打分、条件加权、红线判定、跨对象排名 | 简单的"人工打分"模型无法承载复杂规则 |

### 1.2 技术挑战

在系统设计阶段，需要回答以下关键问题：

```
Q1: 如何设计评估模型的数据结构，使其能表达"多维度、多规则、可嵌套"的业务语义？
Q2: 如何让评分逻辑兼具"规则引擎的确定性"和"AI 的灵活性"？
Q3: 当一次评估涉及数千个对象时，如何保证计算的可扩展性？
Q4: 如何让评估系统能脱离当前业务独立演进，被多个业务域复用？
Q5: 如何设计 AI 集成层，使其不是简单的"调 API"，而是可持续优化的评估闭环？
```

本方案即是针对上述问题的完整技术回答。

---

## 二、总体架构

### 2.1 核心概念模型

评估系统采用**五层概念模型**，**Stage（评估阶段）是核心组织单元**：

```
┌──────────────────────────────────────────────────────────────┐
│  场景（Scene）                                                │
│  Scene = Model 的实例化副本，绑定评估对象                       │
│  一个 Scene 对应一个业务评估任务                                │
├──────────────────────────────────────────────────────────────┤
│  模型（Model）                                                │
│  评估模板：定义 Stage 树 + 指标池 + 事件规则                   │
│  通过 SceneCopy 深拷贝创建 Scene                               │
├──────────────────────────────────────────────────────────────┤
│  ★ Stage（评估阶段/维度树）  ← 系统核心                         │
│  树形结构，三种类型:                                           │
│    TOP    根节点 — JEXL 路由匹配，命中唯一子分支               │
│    NORMAL 中间节点 — 聚合子 Stage 得分 (weighted_sum/sum/min) │
│    LEAF   叶子节点 — 挂载指标，收集 LLM/规则引擎打分           │
│  自底向上聚合: LEAF→NORMAL→TOP，聚合由规则引擎负责（审计底线） │
├──────────────────────────────────────────────────────────────┤
│  指标（Index）                                                │
│  叶子节点的数据点，每个 Index 挂在一个 LEAF Stage 下           │
│  指标自身不参与树结构——它只是 Stage 的数据输入                 │
├──────────────────────────────────────────────────────────────┤
│  对象（Target）/ 属性（Attribute）                             │
│  被评估的业务实体 + 从业务系统拉取的实际值                      │
│  通过 DataPull 注入 EvaluationContext，供指标求值使用           │
└──────────────────────────────────────────────────────────────┘
```

**关键设计决策：Stage 为什么是核心？**

> Stage 不是简单的"分类标签"，而是**评估逻辑的载体**：
> 1. **树结构承载评估层次**：LEAF 收集指标分 → NORMAL 加权聚合 → TOP 路由分叉，整个评估计算是 Stage 树的自底向上遍历
> 2. **权重在 Stage 上，不在指标上**：同一个 Stage 下的指标等权，不同 Stage 按 weight 加权——评估的粒度是 Stage，不是指标
> 3. **LLM 和规则引擎的分界线**：LLM 在 LEAF 层打完分就停下，LEAF 往上的加权求和永远由规则引擎负责，这是审计底线
> 4. **TOP 路由是 Stage 级决策**：根据业务条件（如 "部门=研发"）路由到不同子 Stage 分支，本质上是切换评估策略

**设计决策：为什么模型与场景分离（深拷贝模式）？**

> Model 是模板，Scene 是实例。通过 `SceneCopyDomainService` 深拷贝 Model → Scene：
> 1. Scene 可以独立修改 Stage 树结构、指标分配、事件规则，不影响 Model
> 2. Model 变更不会自动同步到历史 Scene（避免"改模板影响历史评估"）
> 3. 深拷贝包括：Stage 树（parentId 重映射）+ 指标关联（stageId 重映射）

### 2.2 Stage 树聚合（核心算法）

```
示例 Stage 树:
  COST (TOP, weight=100)
  ├── COST_EFFICIENCY (LEAF, weight=60)  → [费用偏差率, 异常次数]
  └── COST_QUALITY (LEAF, weight=40)     → [填报及时率]

聚合过程 (自底向上):
  1. COST_EFFICIENCY: LEAF 聚合 = (LLM_score₁×60 + LLM_score₂×60) / 120
  2. COST_QUALITY:    LEAF 聚合 = LLM_score₃×40 / 40
  3. COST (TOP):      路由匹配 → 取命中分支得分
                      若未命中任何路由 → default fallback 分支

TOP 路由示例:
  COST_EFFICIENCY.routeCondition = 'attrValues["dept"] == "LOGISTICS"'
  → 当被评估对象部门="LOGISTICS"时，走 EFFICIENCY 分支
  → 否则走默认分支（COST_QUALITY）
```

---

## 三、评估引擎架构（Handler 链模式）

### 3.1 改造前（初版设计）

最初的设计是一个**单体评估服务**，所有逻辑写在一个大方法里：

```
evaluate(bizId):
  1. 加载配置（一堆 SQL 查询）
  2. 拉取数据（接口调用）
  3. 匹配标准（if-else 嵌套）
  4. 计算得分（规则执行）
  5. 汇总结果（写入数据库）
  ← 所有步骤耦合在一起，改一个步骤需要理解整个方法
```

**问题**：每次新增评估步骤或修改顺序都需要改动核心方法，无法独立测试单个步骤，且无法扩展为分布式。

### 3.2 改造后（Handler 链+ Pipeline）

```
                    ┌──────────────────────────┐
                    │   EvaluationController   │
                    │   (统一入口/批量调度)      │
                    └───────────┬──────────────┘
                                │
                    ┌───────────▼──────────────┐
                    │   DataPullService        │
                    │   (三路径数据拉取)         │
                    └───────────┬──────────────┘
                                │
                    ┌───────────▼──────────────┐
                    │   ConfigurablePipeline   │
                    │   (固定序 Handler 编排)   │
                    └───────────┬──────────────┘
                                │
          ┌─────────────────────┼─────────────────────┐
          ▼                     ▼                     ▼
   ┌─────────────┐    ┌──────────────┐    ┌─────────────┐
   │  Handler1   │    │   Handler2   │    │   Handler3   │
   │ ValidateAnd │───▶│  Fetch       │───▶│  Calculate   │
   │ LoadModel   │    │  Indicator   │    │  Scores      │
   └─────────────┘    └──────────────┘    └──────┬──────┘
                                                 ▼
                                          ┌─────────────┐
                                          │   Handler4   │
                                          │  Summarize   │
                                          │  Result      │
                                          └──────┬──────┘
                                                 ▼
                                          ┌─────────────┐
                                          │  Ranking    │
                                          │  Handler    │
                                          └─────────────┘
```

**核心设计**：

| 元素 | 职责 | 类似模式 |
|:---|:---|:---|
| **Handler** | 单个处理步骤，接收 Context，输出写入 Context | 职责链（Chain of Responsibility） |
| **Pipeline** | 调度器，按固定顺序执行 Handler | 管道模式（Pipeline Pattern） |
| **EvaluationContext** | 贯穿全链路的上下文，承载所有中间数据 | 上下文对象（Context Object） |
| **Controller** | 批量调度，DataPull → foreach 对象 → Pipeline → Ranking | 门面模式（Facade） |

### 3.3 EvaluationContext 设计

Context 是整个评估流程的数据总线，其结构设计直接决定了 Handler 间的耦合度：

```java
public class EvaluationContext {
    // ===== 输入（由 Controller 在调用 Pipeline 前设置） =====
    private String sceneCode;
    private Long recordId;
    private String bizId;
    private Map<String, Object> rawData;    // 原始业务数据

    // ===== Handler1 产出 =====
    private EvalScene scene;
    private EvalModel model;
    private List<EvalIndex> indices;
    private List<EvalTarget> targets;
    private Map<String, ReferenceStandard> standards;
    private Map<String, DimensionDefinition> dimDefinitions;
    private Map<String, IndexBase> indexBaseMap;

    // ===== Handler2 产出 =====
    private Map<String, Object> rawValues;   // field_code → 实际值
    private Map<String, Object> attrValues;  // field_code → 属性值

    // ===== Handler3 产出 =====
    private List<IndicatorResult> indicatorResults;

    // ===== Handler4 产出 =====
    private Double totalScore;
    private String riskLevel;
    private String aiSummary;
    private IndexLogBase logBase;
    private List<IndexLogItem> logItems;
}
```

**设计原则**：Handler 的输入输出通过 Context 中**明确的字段约定**来传递，而非模糊的 Map。这使得每个 Handler 的依赖和产出都是显式的，便于独立测试和后续并行化。

### 3.4 Handler 详解

#### Handler1：ValidateAndLoadModel

**职责**：根据 sceneCode 和 bizId 加载完整的模型配置。

**加载链路**（6 层数据加载）：

```
scene → model → stages → indices → standards → indexStandards → target
                                                                    │
                                                    dimDefinitions ←┘
                                                    indexBaseMap   ←┘
```

**关键设计**：
- 一次性加载所有配置，避免后续 Handler 重复查库
- 验证配置的完整性（指标是否绑定了标准、标准是否引用了正确的属性）
- 配置变更后通过缓存失效机制确保 Pipeline 拿到的是最新配置

#### Handler2：FetchIndicatorValues

**职责**：将原始业务数据 `rawData` 按指标定义提取为可计算的值。

**处理逻辑**：

```
rawData（Map<String, Object>）
  │
  按 field_code 提取指标值 → rawValues（Map<String, Object>）
  按 field_code 提取属性值 → attrValues（Map<String, Object>）
```

**设计选择**：纯内存操作，不做数据源聚合。当前阶段数据已通过 DataPullService 统一拉取到 rawData 中，Handler2 只做提取和结构转换。多数据源融合、优先级排序放在 DataPullService 层处理。

#### Handler3：CalculateScores（核心计算引擎）

这是整个系统最复杂的 Handler，包含四个子阶段：

```
阶段1：标准匹配（StandardMatcher）
  对每个指标，匹配其适用的参考标准
  匹配策略：模型专属 → 通用标准 → 默认 100 分（三级 fallback）

阶段2：聚合计算（AggregateStageScore）
  按分组聚合模式计算阶段得分
  模式：weighted_sum / sum / min / score_accumulate

阶段3：变量解析（VariableResolver）
  解析 formula 中的 6 种变量引用
  ${val}, ${weight}, ${attr.xxx}, ${dim.xxx}, ${idx.xxx.value}, ${idx.xxx.score}

阶段4：JEXL 求值 + 红线检测
  用 JEXL 引擎执行表达式求值
  检测单指标红线（is_red_line），触发后标记并修正得分
  score_cap / score_floor 截断
  组装 IndicatorResult
```

**6 种变量解析的设计**：

```
变量类型              | 语义                 | 示例
─────────────────────|──────────────────────|────────────────
${val}               | 当前指标的实际值      | ${val} > 80
${weight}            | 当前指标的权重        | ${weight} * 0.01
${attr.xxx}          | 当前对象的属性值      | ${attr.入司天数} < 30
${dim.xxx}           | 指定维度的聚合值      | ${dim.财务组} * 0.6
${idx.xxx.value}     | 其他指标的实际值      | ${idx.日志填报率.value}
${idx.xxx.score}     | 其他指标的得分        | ${idx.客户满意度.score} * 0.3
```

**设计决策：为什么用 JEXL 而不是 Groovy/ScriptEngine？**

| 方案 | 优点 | 缺点 |
|:---|:---|:---|
| JEXL | 轻量、安全（无文件 IO/网络）、语法简单 | 不支持复杂逻辑 |
| Groovy | 语法灵活、功能强大 | 动态执行有安全风险，需沙箱 |
| ScriptEngine (javax.script) | JDK 内置 | 性能差，语法不够友好 |
| SpEL | Spring 原生支持 | 与 Spring 版本耦合，表达式能力有限 |

选用 JEXL 的原因：评估公式本质上是**数学表达式 + 简单条件判断**，JEXL 恰好覆盖这个需求范围。更重要的是 JEXL 默认禁用反射和类加载，天然安全——不需要额外的沙箱保护。

#### Handler4：SummarizeResult

**职责**：汇总评估结果，生成最终报告。

```
calculateTotalScore()  →  加权汇总所有指标得分
resolveRiskLevel()     →  根据总分和红线判定风险等级
writeLog()             →  写入三张日志表（base + item + msg）
generateSummary()      →  调用 AI 生成自然语言评估总结（当前为空壳）
```

**日志设计**（三表结构）：

```
IndexLogBase：每次评估一条记录，存储 bizId、总分、风险等级、排名
IndexLogItem：每个指标一条记录，存储指标得分、权重、红线标记
IndexLogMsg：业务自定义消息，用于扩展输出
```

#### RankingHandler

**职责**：跨对象排序，写回排名。

```
parseRankingConfig()   →  读取排序配置（ASC/DESC）
sortByTotalScore()     →  按总分排序
assignRanks()          →  奥运排名（同分同排名）
writeBackRanks()       →  批量写回 rank / rank_total 到日志表
```

**奥运排名 vs 普通排名**：

```
普通排名：[1, 2, 3, 4, 5]  — 同分不同名
奥运排名：[1, 1, 1, 4, 5]  — 同分同名，下一位跳过重复数
```

选择奥运排名的原因：在业务评估中，得分相同意味着评估结果一致，若给予不同排名会引发公平性质疑。

---

## 四、分布式架构设计

### 4.1 问题背景

单次评估可能涉及数千个对象、每个对象数十个指标的计算。如果在单线程中串行执行，耗时会随对象数线性增长。需要支持**水平扩展**。

### 4.2 架构方案

```
                    ┌──────────────────────────┐
                    │     EvaluationController │
                    │   (调度节点)              │
                    │                          │
                    │  1. DataPull（单节点）    │
                    │  2. 生成 Business IDs    │
                    │  3. 丢入 MQ              │
                    └──────────┬───────────────┘
                               │
                    ┌──────────▼───────────────┐
                    │      RocketMQ            │
                    │  Topic: eval-execution   │
                    └──────────┬───────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
   │ Consumer 1   │   │ Consumer 2   │   │ Consumer N   │
   │              │   │              │   │              │
   │ Handler1-4   │   │ Handler1-4   │   │ Handler1-4   │
   │ + Ranking    │   │ + Ranking    │   │ + Ranking    │
   └──────────────┘   └──────────────┘   └──────────────┘
```

### 4.3 流程分解

```
Step 1（调度节点，单点）:
  Controller.evaluate(sceneCode, bizIds)
    → DataPullService 拉取所有对象的原始数据
    → 为每个 bizId 构造 EvalTaskMessage(bizId, rawData)
    → 发送到 MQ（Topic: eval-execution）

Step 2（消费节点，多点并行）:
  EvalTaskConsumer.onMessage(msg)
    → 重建 EvaluationContext（从 msg 中获取 scopeId）
    → Pipeline.execute(ctx)  ← 每个对象独立走完整 Handler 链
    → 写评估结果到数据库
    → 更新 RecordBase 进度

Step 3（排序节点，单点）:
  所有对象评估完成后
    → RankingHandler 跨对象排序
    → 写回排名
```

### 4.4 关键设计决策

**Q: 为什么 DataPull 放在单节点，而不是分布到各节点？**

```
方案 A（当前选择）：调度节点统一拉取 → 数据封入消息 → 分发到消费节点
方案 B：消息只传 bizId → 消费节点自行拉取数据

选 A 的理由：
1. 数据源单一（目前只有一个业务系统），单节点拉取没有瓶颈
2. 统一拉取可以批量优化（一次接口调用拉取所有对象的数据）
3. 消费节点无状态，不需要配置数据源凭证

选 B 的触发条件：
1. 数据源增多，单节点拉取成为瓶颈
2. 需要消费节点按地域就近拉取数据
```

**Q: 消息体大小限制如何处理？**

单个 EvalTaskMessage 包含 `bizId` + `rawData`。对于大数据场景（如一个对象含数百个属性），当前使用内存传递。若未来数据量超出 MQ 消息体限制（通常 4MB），可引入**指针传递**：消息中只传 `storageKey`，消费节点从共享存储（Redis/OSS）拉取原始数据。

### 4.5 可靠性设计

| 问题 | 方案 |
|:---|:---|
| 消息丢失 | MQ Producer 确认机制 + Consumer ACK |
| 重复消费 | CAS 乐观锁：`UPDATE record_base SET status='RUNNING' WHERE id = ? AND status = 'PENDING'` |
| 消费失败 | try-catch → markFailed → 记录错误堆栈 → 人工补偿 |
| 调度节点宕机 | 已发出的消息不受影响，未发出的任务丢失（当前容忍，后续加补偿 Job） |
| 进度不可见 | `/evaluation/progress` 接口实时查询记录状态 |
| 超时未完成 | TimeoutScanner：定期扫描长时间 RUNNING 的记录，标记为超时 |

---

## 五、AI 集成设计

### 5.1 现有状态 vs 目标架构

```
当前状态（v1.0）:
  规则引擎 → 指标得分 → 汇总总分 → AI 总结（空壳返回 null）
                                             ↑
                                         待接入

目标架构（v1.x）:
                    ┌──────────────────────┐
                    │    AI 评估服务        │
                    │                      │
  规则引擎 ────────▶│  LLM-as-Judge        │
  指标得分          │  RAG 知识库检索      │
  总分              │  上下文组装          │
  红线事件          │  生成评估报告        │
                    └──────────────────────┘
```

### 5.2 LLM 接入点设计

系统预留了三个 AI 接入点：

```
接入点 1：指标级 AI 评分（预留入口）
  位置：Handler3 CalculateScores
  触发条件：指标的 score_mode = 'AI'
  行为：将指标原始值 + 评估标准描述输入 LLM，LLM 返回评分和依据
  现状：未启用，默认用规则引擎

接入点 2：评估总结（核心接入点）
  位置：Handler4 SummarizeResult → generateSummary()
  输入：评估总分 + 各指标得分 + 红线事件 + 对象基本信息
  输出：自然语言的评估总结（如"该商机综合评分 85 分，财务维度表现优秀但交付风险较高"）
  现状：空壳，需接入 LLM 调用

接入点 3：评估报告生成（未来扩展）
  位置：Handler4 之后，异步执行
  行为：基于完整评估数据，生成结构化评估报告（Markdown/PDF）
  现状：未规划
```

### 5.3 Prompt 设计策略（评估总结场景）

```
系统 Prompt：
  你是企业级业务评估分析师。你需要根据以下评估结果，生成简洁、客观的评估总结。
  
  要求：
  1. 先给出总体评价（一句话概括）
  2. 按维度分述表现（每个维度 1-2 句）
  3. 标记风险点（如果有红线事件）
  4. 给出改进建议
  
  语气：客观、专业、数据驱动。
  格式：Markdown 列表。

用户 Prompt 模板：
  ## 评估对象
  - 对象类型：{bizType}
  - 对象 ID：{bizId}
  
  ## 评估结果
  - 总分：{totalScore} / 100
  - 风险等级：{riskLevel}
  
  ## 维度得分
  {dimensionScores}
  
  ## 红线事件
  {redLineEvents}
  
  ## 各指标得分详情
  {indicatorDetails}
```

### 5.4 AI 总结的异步架构（规划）

```
同步路径（当前）：
  Handler4 → generateSummary() → LLM 调用 → 写入日志

异步路径（规划）：
  Handler4 → generateSummary() → 发送 AI_SUMMARY MQ 消息
                                    ↓
                              AISummaryConsumer → LLM 调用
                                    ↓
                              写回评估日志 + 更新状态

异步化的原因：
  1. LLM 调用延迟不可控（1-10s），阻塞 Pipeline 影响整体吞吐
  2. LLM 调用可能失败，异步架构下可重试而不影响主流程
  3. 异步模式下可对 prompt 和模型做更灵活的编排
```

---

## 六、数据飞轮（未来演进）

### 6.1 当前架构的局限

当前系统是**单向评估**——规则/模型输出评估结果，业务方使用结果，但结果的对错没有反馈机制。

```
规则引擎 → 评估结果 → 业务使用
                         ↑
                  没有反馈回路
```

### 6.2 数据飞轮设计

```
                         ┌─────────────────┐
                         │   业务方提供     │
                         │   实际结果数据   │
                         └────────┬────────┘
                                  │
              ┌───────────────────▼───────────────────┐
              │    结果比对引擎                        │
              │    将"系统评估结论"与"实际结果"对比    │
              │    计算偏差率和偏差分布                 │
              └───────────────────┬───────────────────┘
                                  │
              ┌───────────────────▼───────────────────┐
              │    权重自动优化                        │
              │    基于偏差数据，调整各指标权重         │
              │    优化目标：最小化"评估结论 vs 实际"   │
              │    的偏差                              │
              └───────────────────┬───────────────────┘
                                  │
              ┌───────────────────▼───────────────────┐
              │    下一轮评估使用优化后的权重           │
              │    → 评估更接近实际情况                │
              │    → 业务方更信任评估结果              │
              │    → 提供更多数据 → 飞轮加速           │
              └───────────────────────────────────────┘
```

### 6.3 技术实现思考

**权重优化的数学本质**：

```
假设评估总分 = Σ(wi × si)，其中 wi 是指标权重，si 是指标得分
目标是找到一组 wi，使得评估总分与"实际结果"（用某种量化指标表示）的相关性最大化

初步方案：使用线性回归或简单的相关性分析
- 收集历史评估数据（包含各指标得分和实际结果）
- 计算每个指标得分与实际结果的相关系数
- 相关系数高的指标提高权重，低的降低权重
- 约束条件：所有权重之和 = 1，单个权重在 [0.05, 0.3] 区间内
```

**这不是当前的重点，但它为系统预留了进化方向。**

---

## 七、演进路径总览

```
v1.0（当前）              v1.x（近期）                v2.x（远期）
────────────────         ────────────────           ────────────────
单体 Pipeline            MQ 分布式执行             数据飞轮闭环
规则引擎评分             AI 总结接入               权重自动优化
单机串行                 LLM-as-Judge 指标评分     多数据源融合
                        异步 AI 总结               流式计算
                                                 精准评估
改动内容:                改动内容:                   改动内容:
· 评估框架骨架            · AI 总结异步架构           · 结果比对引擎
· Handler 1-4            · RAG 知识库接入            · 权重优化算法
· Ranking Handler        · 语义缓存                 · 数据标注平台
· MQ 分布式执行           · Prompt 版本管理           · A/B 测试框架

风险: 低                 风险: 中                  风险: 高
（已交付）               （需 AI 集成实验）          （需业务数据积累）
```

**演进原则**：

```
1. 每个阶段都是可独立交付的，不依赖后续阶段
2. 前一个阶段为后一个阶段提供数据基础（v1.0 的评估日志是 v1.x AI 总结的输入，
   v1.x 的 AI 结果是 v2.x 数据飞轮的输入）
3. 每一阶段都假设前一阶段会"做错"，所以保留人工干预接口
   （v1.0 保留人工复核，v1.x AI 总结可被人工修改，v2.x 权重优化可被人工覆盖）
```

---

## 八、设计原则总结

### 8.1 关键决策清单

| 决策 | 选择 | 备选方案 | 理由 |
|:---|:---|:---|:---|
| 模型与对象关系 | 一对一 | 一对多 | 避免配置耦合，每场景独立 |
| 评分引擎 | JEXL | Groovy/SpEL | 安全 + 轻量，数学表达式足够 |
| Handler 编排 | 固定序 Pipeline | 动态 DAG | 当前评估流程是线性，DAG 过度设计 |
| 分布式方案 | MQ 消息驱动 | 中心编排器 | 无单点，改造成本低 |
| DataPull | 单节点拉取 | 消费节点自拉 | 当前数据源单一，单节点更高效 |
| 排名策略 | 奥运排名 | 普通排名 | 同分同名，业务公平性要求 |
| AI 集成 | 异步 MQ | 同步阻塞 | 不阻塞主 Pipeline，支持重试 |

### 8.2 架构原则

```
1. Handler 单一职责：每个 Handler 只做一件事，输入输出通过 Context 显式约定
2. Pipeline 固定声明：Handler 顺序在 init() 中硬编码，不用 @Order 注解
3. 批量由调度层管理：Handler 始终处理单个 bizId，不感知批量
4. 最小侵入：复用现有 Entity/Service 层，不修改已有 CRUD 代码
5. 渐进式演进：每个阶段独立交付，不依赖后续阶段
6. 人工兜底：所有自动化环节保留人工干预接口
```

---

## 附录：术语对照

| 术语 | 英文 | 说明 |
|:---|:---|:---|
| 场景 | Scene | 评估执行上下文，关联模型和对象 |
| 模型 | Model | 评估模板，聚合指标和规则 |
| 对象 | Target | 被评估的业务实体 |
| 指标 | Index | 评估维度 |
| 属性 | Attribute | 原始业务数据字段 |
| 参考标准 | Reference Standard | 动态规则，决定指标评分标准 |
| 红线事件 | RedLine Event | 指标触发警戒线后的处理逻辑 |
| Handler | Handler | 评估处理链中的一个步骤 |
| Pipeline | Pipeline | Handler 编排调度器 |
| 上下文 | Context | 贯穿全链路的共享数据载体 |
| 奥运排名 | Olympic Ranking | 同分同排名，后续跳过重复数 |

---

> **文档关联**：
> - [P2-1 评估过程整体框架详细设计](../p2-评估框架/P2-1-评估过程整体框架-详细设计.md) — Pipeline 骨架实现
> - [AI评估组件设计说明书 V1.3](../../archive/AI评估组件设计说明书1.1.md) — 产品级设计规格
> - [评估框架分布式实现文档](../p2-评估框架/评估框架分布式实现文档.md) — 分布式方案细节
> - [AI总结异步架构详细设计](../p2-评估框架/P2-5.1-AI总结异步架构-详细设计-glm5.1.md) — AI 集成方案细节
