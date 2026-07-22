# AI 工程化知识库参考索引

> 本文档列出 working-brain 知识库中与 eval-system 相关的文档。双向链接，确保从项目和知识库都能互查。

### 系统核心思想

- **[AI 评估系统：双通道打分与数据飞轮](../../wiki/analyses/AI评估系统-双通道打分与数据飞轮-20260722.md)**
  系统完整认知文章：打分逻辑、数据飞轮三阶段、RAG 实现、双通道对比、规则引擎的三个角色。
  **用在哪**: 理解系统全局设计、新人入门、技术博客素材。

### A3 RAG 检索增强生成
> 每个条目包含：文档路径、一句话说明、对 eval-system 的具体参考价值。

---

## RAG 检索增强生成

### 核心参考

- **[AI转型实践者手册 — RAG系统工程实战](../../wiki/research/enterprise-ai/AI转型实践者手册-RAG系统工程实战.md)**
  7 种分块策略 + Embedding 模型选型（含中文模型对比表）+ 混合检索架构 + RAG 评估体系（RAGAS/DeepEval）。
  **用在哪**: A3 设计文档的核心参考，RAG 评估指标（Faithfulness/Relevance/Hallucination Rate）可直接引入。

- **[RAG 框架选型与工程落地指南](../../raw/zvos-source/learning/AI-learning/research/rag-framework-selection-guide-2026.md)**
  LangChain/LlamaIndex/Haystack/RAGFlow/Dify 五大框架深度对比，含 LangChain "静默失败" 问题（~8% 空检索直接透传 LLM）。
  **用在哪**: Java 团队 RAG 技术栈决策。Haystack + Hayhooks 推荐为 Java 友好方案。

- **[RAG 框架选型指南（Java 团队视角）](../../raw/zvos-source/learning/AI-learning/research/rag-selection-for-java-teams-2026.md)**
  Sidecar 模式：Python RAG 服务通过 REST API 暴露，Java 负责路由和业务编排。
  **用在哪**: eval-system 是 Java 项目，这个模式解决 Java ↔ Python RAG 生态的桥接问题。

- **[从零精通 RAG（Java 开发者视角）](../../raw/zvos-source/learning/AI-learning/research/rag-from-zero-2026.md)**
  摄入→分块→Embedding→向量检索→Rerank→生成全链路教程，含 7 个常见陷阱。
  **用在哪**: A3 实战参考，7 个陷阱是 A3 测试用例的素材。

- **[RAG 检索质量评测（2026-07-22）](../../wiki/research/RAG-检索质量评测-20260722.md)**
  A3.3 检索质量量化评测：HR@K / NDCG@K 双通道对比 + 人工标注 ground truth + Chart.js 可视化。
  **用在哪**: 向量检索 vs 规则检索的量化对比基准，RAG 质量持续改进的基线。

### 数据库与基础设施

- **[PostgreSQL 18 原生向量搜索](../../wiki/tech-reference/PostgreSQL18-原生向量搜索.md)**
  PG18 内置 VECTOR 类型 + HNSW/IVFFlat 索引，消除"关系库 + 向量库"双架构。
  **用在哪**: 百万级向量规模以下不需要单独向量数据库的决策依据。

---

## AI 系统架构

### 评估系统定位

- **[通用AI系统架构 — 参考设计](../../wiki/通用AI系统架构-参考设计.md)**
  Agent 四层模型（Plan-Retrieve-Generate-Verify）+ Case RAG vs Document RAG 对比 + 五阶段成熟度模型。
  **用在哪**: 核心论断："200-500 条结构化记录时，SQL-based 检索优于向量 RAG"——这是 A3 当前不做向量检索的架构依据。

- **[AI评估系统 — AI嵌入完整路线图](../../wiki/AI评估系统-AI嵌入完整路线图.md)**
  从 BI-first 到 Autonomous Loop 的五阶段路线，Case RAG 的准入条件（200+ 案例记录）。
  **用在哪**: A3 phased rollout 策略，LLM 准入条件的防御机制。

- **[评估系统作为AI基础设施 — Harness与Loop工程](../../wiki/评估系统作为AI基础设施.md)**
  评估系统作为 Verify 组件在 Harness 工程中的定位 + Loop 工程的持续评估-调整-改进循环。
  **用在哪**: eval-system 的整体定位哲学。

### 系统设计文档

- **[AI评估系统 — 架构](../../wiki/projects/AI评估系统架构.md)**
  310+ 文件 / 21 张表 / 21 条 ADR / Pipeline 架构 / DataPullService 三路径取数。
  **用在哪**: 当前系统的完整架构地图，A3/A4 的构建基础。

- **[AI评估系统 — 21条ADR精要](../../wiki/projects/AI评估系统-21条ADR精要.md)**
  6 大主题（Pipeline 哲学/数据流/模型简化/消息可靠性/表达式引擎/边界情况）的架构决策汇总。
  **用在哪**: ADR-003（Pipeline > DAG）、ADR-017（Publisher Confirm）直接为 A4 可靠性提供模式。

- **[AI评估系统 — 核心设计精要](../../wiki/projects/AI评估系统-核心设计精要.md)**
  Pipeline 五步 vs 八步 DAG 决策、DataPullService 三路径、事件机制四类型。
  **用在哪**: 数据检索层设计参考，事件机制为 A4 可靠性检查提供模式。

- **[AI评估系统 — 关键决策形成过程](../../wiki/projects/AI评估系统-关键决策形成过程.md)**
  4 个关键架构决策的完整形成过程（问题发现→根因分析→方案→ADR），从 47 条 AI 对话记录追溯。
  **用在哪**: A3/A4 做新决策时可以复用这个决策形成方法论。

---

## LLMOps 与可靠性工程

### 核心参考

- **[AI转型实践者手册 — LLMOps与生产化实践](../../wiki/research/enterprise-ai/AI转型实践者手册-LLMOps生产化.md)**
  四层可观测性模型 + 评估体系（离线/在线）+ Prompt 工程管理 + 成本管理 + AI CI/CD + Guardrails 三层架构。
  **用在哪**: **A4 模块的核心参考**。2.2-2.4 节的可观测性模型、3.2-3.6 节的评估体系、6.1-6.3 节的 CI/CD 模式可直接落地。

- **[AI转型实践者手册 — AI平台架构设计实践](../../wiki/research/enterprise-ai/AI转型实践者手册-AI平台架构.md)**
  从单体 Spring Boot 到全平台的四阶段演进 + LLM Gateway 设计（路由/限流/语义缓存/故障转移）。
  **用在哪**: A4 可靠性基础设施——LLM Gateway 的限流和故障转移模式可直接套用。

- **[AI转型实践者手册 — Agent与多模型编排实践](../../wiki/research/enterprise-ai/AI转型实践者手册-Agent编排实战.md)**
  ReAct 模式 + Tool 设计 + 多 Agent 拓扑 + 模型路由（简单→中等→复杂模型级联）。
  **用在哪**: 模型路由策略直接为 A4 的多模型 Fallback 链提供模式参考。

---

## AI 架构思维

### 方法论与原则

- **[BI与LLM的正确分层](../../wiki/BI与LLM的正确分层.md)**
  BI 管数据层、LLM 管推理层、人管决策层的三分法，LLM 引入的三个准入条件。
  **用在哪**: 决定"什么交给 AI、什么不交给 AI"的决策框架——A3/A4 的设计原则。

- **[AI原生飞轮 — LLM作为引擎](../../wiki/AI原生飞轮-LLM作为引擎.md)**
  LLM 不是功能调用，是飞轮执行引擎。结构化 Case RAG + 反馈驱动的 few-shot 改进。
  **用在哪**: A3 的 Case RAG 理念来源，反馈驱动改进是 A4 闭环的核心。

- **[维度与指标的本质区别](../../wiki/concepts/维度与指标的本质区别.md)**
  维度（实体属性，不需评分）vs 指标（需要评分）的本质区分，"维度偏见"问题。
  **用在哪**: A3 数据建模正确性的基础——维度用于过滤/分面，不参与评分。

### 架构进化

- **[AI组件进化方向](../../wiki/concepts/AI组件进化方向.md)**
  企业 AI 能力三层进化：L1 API（AI-Ready API 标准）→ L2 MCP（MCP-to-API Gateway）→ L3 Skill（可视化业务脚本）。
  **用在哪**: A4 接口可靠性设计，AI-Ready API 标准化。

- **[AI应用架构师 — 执行路线图](../../raw/zvos-source/learning/ai-architect/AI应用架构师-执行路线图.md)**
  项目驱动的 AI 架构师学习路线：生产级改造（Prompt + 可观测性）→ AI 评估 AI 化（RAG + Agent）→ 模型部署（LLMOps）。
  **用在哪**: A3/A4 模块的自身定位参考。

- **[跨模型审阅最佳实践](../../wiki/methodology/跨模型审阅最佳实践.md)**
  审阅者不应看到作者起草过程的三轮跨模型审阅方法论。
  **用在哪**: A4 可靠性验证——独立交叉验证提升评估可靠性。

---

## 阅前指南

**如果你只有 2 小时**，按这个顺序看：

1. `通用AI系统架构-参考设计.md` — 建立 AI 系统整体认知
2. `AI评估系统-AI嵌入完整路线图.md` — 理解 eval-system 的 AI 化节奏
3. `BI与LLM的正确分层.md` — 理解"什么给 AI、什么不给"的核心判断

**如果你想直接干活（A3/A4）**，按这个顺序看：

1. `AI转型实践者手册-RAG系统工程实战.md` — A3 技术选型依据
2. `AI转型实践者手册-LLMOps生产化.md` — A4 核心参考
3. `AI转型实践者手册- Agent编排实战.md` — A4 模型路由参考
4. `AI评估系统-架构.md` + `AI评估系统-21条ADR精要.md` — 当前系统基础
