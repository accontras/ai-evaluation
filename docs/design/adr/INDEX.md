# AI 评估系统 — 架构决策记录（ADR）

**项目**：AI 评估系统
**创建日期**：2026-05-15
**状态说明**：所有 ADR 默认状态为 **Accepted**

---

## 索引

| 编号 | 标题 | 状态 |
|---|---|---|
| ADR-001 | 统一评估接口，自动判断同步/异步 | Accepted |
| ADR-002 | 管道不感知批量，循环调度在 Controller 层 | Accepted |
| ADR-003 | 采用固定线性管道，不引入 DAG 编排 | Accepted |
| ADR-004 | Handler 顺序显式写死列表，不使用 @Order 注解 | Accepted |
| ADR-005 | 红线检测内嵌于 CalculateScoresHandler，不拆分为独立 Handler | Accepted |
| ADR-006 | DATA-PULL 只在 Controller 执行一次，Handler2 纯内存操作 | Accepted |
| ADR-007 | 用 dr_data_dimension.field_code 替代属性子表做维度映射 | Accepted |
| ADR-008 | 用 target_code 字段直接关联评估对象，替代关系表 | Accepted |
| ADR-009 | 聚合模式多级 Fallback（Stage → Model → 默认） | Accepted |
| ADR-010 | 低频配置放 options JSON，不建独立字段 | Accepted |
| ADR-011 | AI 总结 Handler 必须编写，MVP 阶段逻辑放空 | Accepted |
| ADR-012 | 引入评估记录表作为日志上级，支撑批量关联 | Accepted |
| ADR-013 | 表达式引擎复用 JEXL，变量预处理后求值 | Accepted |
| ADR-014 | 跨指标变量绑定，按 sn 升序处理并检测循环引用 | Accepted |
| ADR-015 | DATA-PULL 三路径设计（传 data / viewCode 分组取数 / queryMetricFromSql 兜底） | Superseded by ADR-021 |
| ADR-016 | 奥运排名方式处理同分并列 | Accepted |
| ADR-017 | 评估任务消息启用 Publisher Confirm 异步确认，专用 Template 隔离 | Accepted |
| ADR-018 | 模型标准新增 score_mode 字段，显式指定得分计算方式 | Accepted |
| ADR-019 | 扩大 attrValues 而非绕过它，dimDefinitions 作为数据源无关的翻译层 | Accepted |
| ADR-020 | AI总结复用评估任务 ConnectionFactory，不建独立连接池 | Accepted |
| ADR-021 | DataPullService 取数策略重构：viewCode 分组驱动，替代逐指标循环 | Accepted |

---


---

## 独立 ADR 文件

- [ADR-001：统一评估接口，自动判断同步/异步](ADR-001-统一评估接口-自动判断同步异步.md)
- [ADR-002：管道不感知批量，循环调度在 Controller 层](ADR-002-管道不感知批量-循环调度在 Controller 层.md)
- [ADR-003：采用固定线性管道，不引入 DAG 编排](ADR-003-采用固定线性管道-不引入 DAG 编排.md)
- [ADR-004：Handler 顺序显式写死列表，不使用 @Order 注解](ADR-004-Handler 顺序显式写死列表-不使用 @Order 注解.md)
- [ADR-005：红线检测内嵌于 CalculateScoresHandler，不拆分为独立 Handler](ADR-005-红线检测内嵌于 CalculateScoresHandler-不拆分为独立 Handler.md)
- [ADR-006：DATA-PULL 只在 Controller 执行一次，Handler2 纯内存操作](ADR-006-DATA-PULL 只在 Controller 执行一次-Handler2 纯内存操作.md)
- [ADR-007：用 dr_data_dimension.field_code 替代属性子表做维度映射](ADR-007-用 dr_data_dimension.field_code 替代属性子表做维度映射.md)
- [ADR-008：用 target_code 字段直接关联评估对象，替代关系表](ADR-008-用 target_code 字段直接关联评估对象-替代关系表.md)
- [ADR-009：聚合模式多级 Fallback（Stage → Model → 默认）](ADR-009-聚合模式多级 Fallback（Stage → Model → 默认）.md)
- [ADR-010：低频配置放 options JSON，不建独立字段](ADR-010-低频配置放 options JSON-不建独立字段.md)
- [ADR-011：AI 总结 Handler 必须编写，MVP 阶段逻辑放空](ADR-011-AI 总结 Handler 必须编写-MVP 阶段逻辑放空.md)
- [ADR-012：引入评估记录表作为日志上级，支撑批量关联](ADR-012-引入评估记录表作为日志上级-支撑批量关联.md)
- [ADR-013：表达式引擎复用 JEXL，变量预处理后求值](ADR-013-表达式引擎复用 JEXL-变量预处理后求值.md)
- [ADR-014：跨指标变量绑定，按 sn 升序处理并检测循环引用](ADR-014-跨指标变量绑定-按 sn 升序处理并检测循环引用.md)
- [ADR-015：DATA-PULL 三路径设计（传 data / queryCondition / 默认配置）](ADR-015-DATA-PULL 三路径设计（传 data  queryCondition  默认配置）.md)
- [ADR-016：奥运排名方式处理同分并列](ADR-016-奥运排名方式处理同分并列.md)
- [ADR-017：评估任务消息启用 Publisher Confirm 异步确认，专用 Template 隔离](ADR-017-评估任务消息启用 Publisher Confirm 异步确认-专用 Template 隔离.md)
- [ADR-018：模型标准新增 score_mode 字段，显式指定得分计算方式](ADR-018-模型标准新增 score_mode 字段-显式指定得分计算方式.md)
- [ADR-019：扩大 attrValues 而非绕过它，dimDefinitions 作为数据源无关的翻译层](ADR-019-扩大 attrValues 而非绕过它-dimDefinitions 作为数据源无关的翻译层.md)
- [ADR-020：AI总结复用评估任务 ConnectionFactory，不建独立连接池](ADR-020-AI总结复用评估任务 ConnectionFactory-不建独立连接池.md)
- [ADR-021：DataPullService 取数策略重构：viewCode 分组驱动，替代逐指标循环](ADR-021-DataPullService 取数策略重构-viewCode 分组驱动-替代逐指标循环.md)
