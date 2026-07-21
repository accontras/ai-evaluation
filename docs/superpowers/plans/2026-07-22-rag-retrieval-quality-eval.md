---
change: rag-retrieval-quality-eval
design-doc: docs/superpowers/specs/2026-07-22-rag-retrieval-quality-eval-design.md
base-ref: 9ea09bfa9ec5f26baa8dfaa8c5f66068e5c1998f
---

# RAG 检索质量评测 — 实施计划

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans to implement task-by-task.

**Goal:** 对 A3.1/A3.2 已完成的 RAG pipeline 进行检索质量量化评测，计算 HR@K 和 NDCG@K 指标，输出可视化报告。

**Architecture:** 新增 `RagQualityService` 进行纯计算，`RagCompareTracker` 改造增加 DB 写入，`RagQualityEvalRunner` 作为 CommandLineRunner（profile=rag-eval）加载 ground truth，逐条检索后汇总计算 → 输出 JSON + Chart.js HTML。

**Tech Stack:** Java 17, Spring Boot 3.3.5, MyBatis-Plus 3.5.7, MySQL 8.4, Chart.js (CDN)

## Global Constraints

- Java 17, Spring Boot 3.3.5, MyBatis-Plus 3.5.7
- 所有新增类遵循 DDD 分层：domain → infrastructure → application → boot
- Entity 使用 MyBatis-Plus 注解，手动 getter/setter（无 Lombok）
- Mapper 继承 `BaseMapper<T>`，标注 `@Mapper`
- SQL 迁移脚本存 `docs/sql/`，命名 `V{NNN}__{name}.sql`
- 新类包名：`io.github.accontra.eval.{domain|infrastructure|application|boot}.{subpackage}`

---

### Task 1: Ground Truth 标注数据集

**Files:** Create `data/rag-ground-truth.json`

- [ ] 1.1 从 eval_indicator_log 筛选 8-12 条代表性查询（覆盖不同指标类型和数值范围）
- [ ] 1.2 合成 5-8 条边界查询（极端值、无数据、稀疏指标）
- [ ] 1.3 标注每条查询的理想 Top-3 检索结果（人工判断历史案例相关性）
- [ ] 1.4 创建 `data/rag-ground-truth.json`，含 id/indexCode/indexName/dataValue/relevantLogIds/notes

### Task 2: 数据库表 + Entity + Mapper

**Files:** Create DDL, Entity, Mapper

- [ ] 2.1 创建 `docs/sql/V010__rag_compare_log.sql` DDL
- [ ] 2.2 创建 Entity: `eval-domain/.../model/EvalRagCompareLog.java`（MyBatis-Plus 注解，手动 getter/setter）
- [ ] 2.3 创建 Mapper: `eval-infrastructure/.../mapper/EvalRagCompareLogMapper.java`（继承 BaseMapper）
- [ ] 2.4 编译验证: `mvn compile -q`

### Task 3: RagCompareTracker 改造

**Files:** Modify RagCompareTracker.java, LlmScoringStrategy.java (编译适配)

- [ ] 3.1 RagCompareTracker 新增 11 参数重载 `record()` 方法（含完整字段 + DB 写入）
- [ ] 3.2 保留原 5 参数方法（@Deprecated），增加 DB 写入（ground_truth_rel=NULL）
- [ ] 3.3 LlmScoringStrategy 编译适配（Spring 自动注入 handle 新依赖）
- [ ] 3.4 编译验证: `mvn compile -q`

### Task 4: 量化指标计算 RagQualityService

**Files:** Create RagQualityService.java, RagQualityServiceTest.java

- [ ] 4.1 编写单元测试（TDD 先写测试，8 个用例：全命中/全不中/空输入/最佳排序/次优排序/全不相关/多查询/K>结果数）
- [ ] 4.2 实现 RagQualityService.hrAtK() — Hit Rate@K 计算
- [ ] 4.3 实现 RagQualityService.ndcgAtK() — NDCG@K 计算（二元相关，边界 DCG=0→NDCG=1.0）
- [ ] 4.4 运行测试通过: `mvn test -pl eval-application -Dtest=RagQualityServiceTest`

### Task 5: 跑批评测脚本 RagQualityEvalRunner

**Files:** Create RagQualityEvalRunner.java, modify application.yml

- [ ] 5.1 创建 `RagQualityEvalRunner`（@Profile("rag-eval"), CommandLineRunner）
- [ ] 5.2 application.yml 添加 rag-eval profile（web-application-type: none）
- [ ] 5.3 实现 6 步工作流: 检查可用性 → 加载 ground truth → 逐条检索 → 汇总计算 → 输出 results.json → 打印控制台对比表
- [ ] 5.4 编译验证: `mvn compile -q`

### Task 6: 图表生成

**Files:** Create `data/rag-eval-results/index.html`

- [ ] 6.1 创建 HTML 报告页面：汇总对比表 + Chart.js 柱状图（HR@K）+ 折线图（NDCG@K）
- [ ] 6.2 通过 fetch('results.json') 加载评测数据动态渲染

### Task 7: 实验笔记

**Files:** Create wiki/research note, modify docs/AI-KNOWLEDGE-REF.md

- [ ] 7.1 撰写 `wiki/research/RAG-检索质量评测-20260722.md`（实验目的 + 数据说明 + 结果表 + 图表引用 + 结论）
- [ ] 7.2 更新 `docs/AI-KNOWLEDGE-REF.md` 添加指向实验笔记的链接

---

## Dependency Sequencing

```
Task 1 (Ground Truth) ──────────┐
                                 │
Task 2 (DDL+Entity+Mapper) ─┐   │        Task 4 (RagQualityService) ──┐
                             │   │                                    │
Task 3 (RagCompareTracker) ──┘   │          ◄─────────────────────────┘
                                 │                                    │
                      Task 5 (RagQualityEvalRunner) ◄──────────────────┘
                                 │
                      Task 6 (HTML Report)
                                 │
                      Task 7 (Experiment Notes)
```
