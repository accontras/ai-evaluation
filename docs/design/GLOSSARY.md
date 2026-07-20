# 术语表

> AI 评估系统核心领域术语。

## 核心概念

| 术语 | 英文 | 定义 |
|---|---|---|
| 评估模型 | Model | 评估模板，定义 Stage 树 + 指标池 + 事件规则。自身不直接用于评估，必须深拷贝为方案。对应 `eval_model` |
| 评估方案（场景） | Scene | Model 的实例化副本 = 一个可执行的评估方案。**Scene 和"方案"是同一个概念**。通过 `SceneCopyDomainService` 深拷贝创建。对应 `eval_scene` |
| ★ Stage | Stage | **系统的核心组织单元**。树形节点，三种类型：TOP（路由）/ NORMAL（聚合）/ LEAF（叶子+挂指标）。整个评估计算是 Stage 树的自底向上遍历。对应 `eval_model_stage` + `eval_scene_stage` |
| 指标 | Index | 叶子节点的数据点，挂载在 LEAF Stage 下。指标自身不参与树结构。对应 `eval_index` |
| 评估对象 | Target | 被评估的业务实体，通过 `target_code` 关联。对应 `eval_target` |
| 评估记录 | Object Log | 一次评估的结果记录（总分/等级/排名/风险），对应 `eval_object_log` |

## Stage 树

| 术语 | 英文 | 定义 |
|---|---|---|
| TOP | Top Stage | 根节点，JEXL 路由匹配命中唯一子分支，实现动态评估策略切换 |
| NORMAL | Normal Stage | 中间节点，聚合子 Stage 得分 (weighted_sum / sum / min) |
| LEAF | Leaf Stage | 叶子节点，挂载指标，收集 LLM/规则引擎打分。**LLM 在这里停下，往上全是规则引擎数学** |
| 树聚合 | Tree Aggregation | 自底向上遍历 Stage 树：LEAF 收集指标分 → NORMAL 加权聚合 → TOP 路由。由 `TreeAggregator` 执行 |
| 路由条件 | Route Condition | TOP Stage 子分支的 JEXL 条件，如 `attrValues["dept"] == "LOGISTICS"`，命中走该分支 |

## 评分体系

| 术语 | 英文 | 定义 |
|---|---|---|
| LLM-as-Judge | LLM Scoring | LLM 语义理解打分，替代硬编码阈值区间。由 `LlmScoringStrategy` 执行 |
| 规则引擎评分 | Rule Scoring | JEXL 表达式 + 区间匹配 + 三级 Fallback。由 `RuleScoreStrategy` 执行 |
| 双通道对比 | Dual Channel | LLM 和规则引擎并行打分，逐指标对比差异：TRIVIAL (<5分) / NOTABLE (5-15分) / SIGNIFICANT (≥15分) |
| 降级分数 | Degraded Score | LLM 不可用时，所有指标默认 70 分 |
| 等级映射 | Grade Mapping | 分数 → 等级 (S/A/B/C/D)，SCORE_RANGE 区间匹配 |
| Score Cap / Floor | Cap/Floor | 得分上下限钳制，防止极端值 |

## Pipeline

| 术语 | 英文 | 定义 |
|---|---|---|
| Pipeline | Pipeline | 固定线性处理链：H1 加载 → H2 取数 → H3 打分+树聚合 → H4 事件检测 → H6 汇总落库 |
| Handler | Handler | Pipeline 中的单个处理步骤，统一接口 (stepCode / order / execute / shouldSkip) |
| EvaluationContext | Context | 贯穿全链路的数据载体，承载配置/原始数据/中间结果/最终产出 |
| StageNode | Stage Node | 内存中的 Stage 树节点，由 `StageNodeAssembler` 从扁平的 Stage 列表装配 |

## 事件与申诉

| 术语 | 英文 | 定义 |
|---|---|---|
| 红线事件 | Red-Line Event | 一票否决规则，触发后 blocked=true，总分 ×0.6。双通道检测（RULE JEXL + LLM 语义） |
| 事件类型 | Event Type | RED_LINE（红线）/ BONUS（加分）/ DEDUCT（扣分）/ MARK（标记） |
| 触发来源 | Trigger Source | RULE / LLM / BOTH — 标记事件是规则引擎还是 LLM 触发的 |
| 申诉 | Appeal | 对被评估结果的质疑，支持 BONUS / PENALTY / TOTAL 三种类型。审批后自动重算 |
| 奥运排名 | Olympic Ranking | 同分并列 (1,1,3,4...)，对应 ADR-016 |

## AI 能力

| 术语 | 英文 | 定义 |
|---|---|---|
| LLM 打分 | LLM-as-Judge | H3: LLM 对每个指标独立打分 0-100 + 理由。temperature=0.3 |
| LLM 异常检测 | LLM Event Detection | H4: LLM 判断是否存在数据异常/红线风险，与规则引擎交叉验证 |
| AI 总结 | AI Summary | H6: 两轮对话——Round1 生成评估总结 → Round2 自审修正 |
| 两轮自审 | Two-Round Review | Round1 生成初稿 → Round2 检查遗漏/措辞/数据引用 → 输出修正版 |

## 技术概念

| 术语 | 英文 | 定义 |
|---|---|---|
| JEXL | Java Expression Language | 表达式引擎，用于事件条件、路由条件、得分计算 |
| 深拷贝 | Deep Copy | 从 Model 模板创建 Scene/方案时，复制 Stage 树（parentId 重映射）+ 指标关联（stageId 重映射） |
| Caffeine | Caffeine Cache | 本地缓存，TTL 5min，缓存 scene→model→stages→indices |
| 决策记录 | ADR | Architecture Decision Record，架构决策记录 |
