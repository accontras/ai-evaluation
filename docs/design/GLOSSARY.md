# 术语表

> AI 评估系统核心领域术语。

## 核心概念

| 术语 | 英文 | 定义 |
|---|---|---|
| 评估模型 | Model | 评价体系的顶层容器，定义评价的维度树、指标、评分标准和事件规则。对应 `dr_model_base` |
| 评估场景 | Scene | 模型的一次具体应用实例，绑定评估对象并配置数据源。对应 `dr_model_scene` |
| 评估方案 | Scheme | 从模型拷贝出的独立副本（深拷贝隔离），场景落地为可执行的方案。含维度树、指标关联、参考标准 |
| 评估对象 | Evaluation Target | 被评价的业务实体（人员/组织/项目），通过 `target_code` 关联。对应 `dr_evaluation_target` |
| 评估记录 | Evaluation Record | 一次评估任务的元数据记录（批次号、状态、起止时间）。对应 `dr_evaluation_record_base` |

## 维度体系

| 术语 | 英文 | 定义 |
|---|---|---|
| Stage（维度节点） | Stage | 评估模型的分层维度树节点。类型：`top`（路由层）/ `normal`（中间汇总）/ `leaf`（叶子算分） |
| 维度树 | Stage Tree | 模型→维度→指标三层体系的物理载体，支持条件分支路由。对应 `dr_model_stage` |
| 路由层 | Top Stage | `type=top` 的根节点，按条件（如入职天数）命中唯一子分支，实现动态权重 |
| 中间维度 | Normal Stage | `type=normal` 的节点，做子节点权重汇总聚合，不直接算分 |
| 叶子维度 | Leaf Stage | `type=leaf` 的节点，挂载指标和参考标准，负责实际算分 |
| 条件分支 | Conditional Branch | Top Stage 的多条子路径，每条对应一个条件区间，各自有独立的权重分配 |

## 指标与评分

| 术语 | 英文 | 定义 |
|---|---|---|
| 业务指标 | Index | 可量化的评价数据点（如日志填报率、拜访次数、销售收入）。对应 `dr_model_index` |
| 参考标准 | Reference Standard | 指标的分值映射规则（分数区间/公式/固定分）。对应 `dr_model_index_standard` |
| 评分模式 | Score Mode | 四种得分计算方式：`RAW_WEIGHT`（原始值乘权重）/ `FIXED`（固定分）/ `FIXED_WEIGHT`（固定分乘权重）/ `INTERVAL_WEIGHT`（区间权重） |
| 聚合模式 | Aggregate Mode | 子维度得分汇总方式：`weighted_avg`（加权平均）/ `weighted_sum`（加权求和）/ `sum`（直接求和）/ `min`（取最小值） |
| 派生指标 | Derived Index | `calculate_type=DERIVED` 的指标，其值由其他指标按公式计算得出 |
| 归一化 | Normalization | 将不同量纲的指标得分统一到可比较的尺度 |

## Pipeline 处理链

| 术语 | 英文 | 定义 |
|---|---|---|
| Pipeline | Pipeline | 固定线性处理链：ValidateAndLoad → FetchIndicator → CalculateScores → EventApply → SummarizeResult |
| Handler | Handler | Pipeline 中的单个处理步骤，遵循统一接口 |
| EvaluationContext | Context | 贯穿单对象 Pipeline 的数据载体，包含配置、原始数据、中间结果 |
| DataPullService | DataPullService | 取数服务，支持三路径：传 data / viewCode 分组取数 / queryMetricFromSql 兜底 |
| 三路径 | Three-Path Data Pull | 路径A（直接传 data）、路径B（viewCode 分组取数，主力通道）、路径C（queryMetricFromSql 兜底） |

## 事件体系

| 术语 | 英文 | 定义 |
|---|---|---|
| 红线事件 | Red-Line Event | 一票否决规则，触发后得分归零或修正。内嵌于 CalculateScoresHandler |
| 事件积分 | Event Score | 加分/减分事件（如重大贡献加分、违规扣分），可配置触发条件和动作 |
| 等级映射 | Grade Mapping | 分数字 → 等级（A/B/C/D/E）的映射关系，支持分数区间模式和排名百分比模式 |

## 申诉体系

| 术语 | 英文 | 定义 |
|---|---|---|
| 申诉 | Appeal | 被评价人对评价结果的质疑申请，支持加分/减分/总分申诉 |
| 申诉重算 | Appeal Re-evaluation | 对申诉涉及的对象重新执行评估计算 |
| 公示期 | Public Review Period | 评价结果发布后的申诉窗口期，仅公示状态可申诉 |

## 技术概念

| 术语 | 英文 | 定义 |
|---|---|---|
| JEXL | Java Expression Language | 规则表达式引擎，用于动态求值计算规则 |
| Publisher Confirm | Publisher Confirm | RabbitMQ 异步确认机制，确保评估任务消息可靠投递 |
| 深拷贝隔离 | Deep Copy Isolation | 方案创建时从模型完整拷贝为独立副本，方案与模型生命周期解耦 |
| 幂等保护 | Idempotency Guard | 评估执行接口的防重复提交机制 |
| 两阶段结果模型 | Two-Phase Result | 评分结果同步返回 + AI 总结异步回填 |
| Score Cap / Floor | Score Cap/Floor | 得分上下限钳制，防止极端值 |
