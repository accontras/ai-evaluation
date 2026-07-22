* **📖 系统全书**
  * [系统全书](docs/BOOK.md)

* **🤖 AI 功能**
  * [AI 实现详解](docs/AI-IMPLEMENTATION.md)
  * [AI 知识参考](docs/AI-KNOWLEDGE-REF.md)

* **📐 设计文档**
  * [LLM 打分设计](docs/design/LLM-SCORING-DESIGN.md)
  * [Prompt 版本化](docs/design/A1.2-PROMPT-VERSIONING.md)
  * [LLM 可观测性](docs/design/A2-LLM-OBSERVABILITY.md)
  * [架构设计](docs/design/ai-evaluation-system-architecture.md)
  * [术语表](docs/design/GLOSSARY.md)
  * [技术方案](docs/design/TECHNICAL-PLAN.md)

* **📋 架构决策 ADR**
  * [ADR 索引](docs/design/adr/INDEX.md)
  * [ADR-001: 统一接口](docs/design/adr/ADR-001-统一评估接口-自动判断同步异步.md)
  * [ADR-002: 批量调度](docs/design/adr/ADR-002-管道不感知批量-循环调度在 Controller 层.md)
  * [ADR-003: 固定管道](docs/design/adr/ADR-003-采用固定线性管道-不引入 DAG 编排.md)
  * [ADR-004: Handler 顺序](docs/design/adr/ADR-004-Handler 顺序显式写死列表-不使用 @Order 注解.md)
  * [ADR-005: 红线内嵌](docs/design/adr/ADR-005-红线检测内嵌于 CalculateScoresHandler-不拆分为独立 Handler.md)
  * [ADR-006: DATA-PULL](docs/design/adr/ADR-006-DATA-PULL 只在 Controller 执行一次-Handler2 纯内存操作.md)
  * [ADR-007: 维度映射](docs/design/adr/ADR-007-用 dr_data_dimension.field_code 替代属性子表做维度映射.md)
  * [ADR-008: target_code](docs/design/adr/ADR-008-用 target_code 字段直接关联评估对象-替代关系表.md)
  * [ADR-009: 聚合 Fallback](docs/design/adr/ADR-009-聚合模式多级 Fallback（Stage → Model → 默认）.md)
  * [ADR-010: options JSON](docs/design/adr/ADR-010-低频配置放 options JSON-不建独立字段.md)
  * [ADR-011: AI 总结占位](docs/design/adr/ADR-011-AI 总结 Handler 必须编写-MVP 阶段逻辑放空.md)
  * [ADR-012: 评估记录表](docs/design/adr/ADR-012-引入评估记录表作为日志上级-支撑批量关联.md)
  * [ADR-013: JEXL 复用](docs/design/adr/ADR-013-表达式引擎复用 JEXL-变量预处理后求值.md)
  * [ADR-014: 循环引用](docs/design/adr/ADR-014-跨指标变量绑定-按 sn 升序处理并检测循环引用.md)
  * [ADR-015: 三路径](docs/design/adr/ADR-015-DATA-PULL 三路径设计（传 data  queryCondition  默认配置）.md)
  * [ADR-016: 奥运排名](docs/design/adr/ADR-016-奥运排名方式处理同分并列.md)
  * [ADR-017: Publisher Confirm](docs/design/adr/ADR-017-评估任务消息启用 Publisher Confirm 异步确认-专用 Template 隔离.md)
  * [ADR-018: score_mode](docs/design/adr/ADR-018-模型标准新增 score_mode 字段-显式指定得分计算方式.md)
  * [ADR-019: attrValues](docs/design/adr/ADR-019-扩大 attrValues 而非绕过它-dimDefinitions 作为数据源无关的翻译层.md)
  * [ADR-020: 连接池复用](docs/design/adr/ADR-020-AI总结复用评估任务 ConnectionFactory-不建独立连接池.md)
  * [ADR-021: viewCode 分组](docs/design/adr/ADR-021-DataPullService 取数策略重构-viewCode 分组驱动-替代逐指标循环.md)

* **🔍 RAG & 可靠性**
  * [RAG 检索评测](docs/A3.3-RAG-retrieval-eval-design.md)
  * [RAG 阻塞项](docs/A3-RAG-BLOCKER.md)
  * [AI 可靠性工程](docs/A4-AI-reliability-engineering-design.md)

* **📊 Superpowers**
  * **Plans**
    * [LLM 韧性升级](docs/superpowers/plans/2026-07-22-llm-resilience-upgrade.md)
    * [RAG 检索质量](docs/superpowers/plans/2026-07-22-rag-retrieval-quality-eval.md)
  * **Reports**
    * [LLM 韧性验证](docs/superpowers/reports/2026-07-22-llm-resilience-upgrade-verify.md)
    * [RAG 迁移验证](docs/superpowers/reports/2026-07-22-migrate-rag-to-qdrant-verify.md)
    * [RAG 检索验证](docs/superpowers/reports/2026-07-22-rag-retrieval-quality-eval-verify.md)
  * **Specs**
    * [LLM 韧性设计](docs/superpowers/specs/2026-07-22-llm-resilience-upgrade-design.md)
    * [RAG Qdrant 设计](docs/superpowers/specs/2026-07-22-migrate-rag-to-qdrant-design.md)
    * [RAG 检索设计](docs/superpowers/specs/2026-07-22-rag-retrieval-quality-eval-design.md)

* **📝 项目文档**
  * [README](README.md)
  * [AGENTS 编码规范](AGENTS.md)
  * [CHANGELOG](CHANGELOG.md)
  * [开发计划](DEVELOPMENT-PLAN.md)
  * [执行明细](PLAN-DETAIL.md)
