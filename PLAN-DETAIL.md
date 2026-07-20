# AI 评估系统 — 执行明细日志

> 状态图例: ⬜ 待做 | 🔄 进行中 | ✅ 完成 | ⏭️ 跳过 | ❌ 阻塞

---

## 当前: S15 — Stage 树装配 + 树聚合

**开始时间**: 待定 | **预计**: 3 小时 | **状态**: ⬜

---

## S14: 对比数据持续积累 ✅

**完成时间**: 2026-07-20 | **实际耗时**: ~1 小时

### 产出

| 文件 | 改动 |
|------|------|
| `V005__dual_channel_fields.sql` | DDL: 5 个对比字段 |
| `EvalIndicatorLog` | llmScore, ruleScore, scoreDiff, diffLevel, llmReason |
| `LlmScoringStrategy` / `RuleScoreStrategy` / `DualChannelScoringService` | `@Component` Bean 化 |
| `LlmCalculateScoresHandler` | 双通道并行: LLM + 规则 + 对比 |
| `SummarizeResultHandler` | 落库写入对比数据 |
| `EvaluationContext` | ruleScores + indicatorDiffs |
| `EvaluationController` | `/compare/stats` 统计 API |
| `pom.xml` | `-parameters` 编译参数 |

### 验证结果

```
POST /execute → scoringMode: DUAL_CHANNEL(SIG:2) ✅
GET  /compare/stats → { totalCompared:3, significantRate:66.7%, avgDiff:49.77 } ✅
DB   → llm_score/rule_score/score_diff/diff_level 完整落库 ✅
```

---

## S10: M1 端到端验证 ✅

**完成时间**: 2026-07-20 | **实际耗时**: ~1.5 小时

### 验证结果

| 步骤 | 结果 |
|------|------|
| 数据库 eval_db 25张表 | ✅ |
| 种子数据 (V004) | ✅ LOGISTICS-2026Q2 + LOGISTICS_COST + 3指标 |
| mvn compile | ✅ 零错误 |
| /actuator/health | ✅ UP |
| curl POST /api/v1/evaluation/execute | ✅ 200 OK |
| LLM 打分 (DeepSeek) | ✅ totalScore=76.67, 非降级分 |
| 落库 scoringMode="LLM" | ✅ task_log + object_log + indicator_log |

### 真实 LLM 打分结果

```
COST_DEV  费用偏差率     70分  "9.2%处于中等偏高水平，接近警戒线"
ABNORM_CNT 异常波动次数  85分  "2次属较低水平，业务运行相对稳定"
FILL_RATE 填报及时率     75分  "85.5%略低于常见目标值"
─────────────────────────────────
totalScore: 76.67  |  riskLevel: MEDIUM
```

### 额外产出

| 文件 | 说明 |
|------|------|
| `EvaluationController.java` | POST /api/v1/evaluation/execute |
| `ExecuteEvaluationRequest.java` | Record: sceneCode, bizId, dataPeriod, data |
| `ExecuteEvaluationResponse.java` | Record: totalScore, riskLevel, indicators[] |
| `EvaluationContext.llmScores` | 透传 LLM 打分到 Controller |
| `application.yml` | API key 默认值 fallback |

---

## S4-S9, S11-S13: 批量完成 ✅

**完成时间**: 2026-07-20 | **备注**: 在一次长会话中连续完成 S4-S13

| Session | 产出摘要 |
|---------|---------|
| S4 ✅ | 25 Entity + 25 Mapper (extends BaseMapper) |
| S5 ✅ | Handler 接口 + ConfigurablePipeline + EvaluationContext + PingHandler |
| S6 ✅ | LlmClient (OpenAI兼容) + LlmConfig + PromptTemplate |
| S7 ✅ | DataPullService + FetchIndicatorValuesHandler (维度声明+ADR-019补齐) |
| S8 ✅ | LlmScoringStrategy (LLM-as-Judge) + LlmCalculateScoresHandler + 降级70分 |
| S9 ✅ | ValidateAndLoadModelHandler (H1) + SummarizeResultHandler (H6) |
| S11 ✅ | ExpressionUtil (JEXL沙箱) + 变量预处理 |
| S12 ✅ | RuleScoreStrategy (区间/字典/表达式匹配 + 三级Fallback + 4种score_mode) |
| S13 ✅ | DualChannelScoringService (双通道对比 + TRIVIAL/NOTABLE/SIGNIFICANT差异分级) |

### 额外完成 (未在计划中明确列出)

| 组件 | 说明 |
|------|------|
| `eval-infrastructure/` 模块 | 独立模块, 含 25 Mapper + 5 ServiceImpl + DataPullService + LLM集成 |
| `GlobalExceptionHandler` | 统一异常处理 |
| `HealthController` | `/actuator/health` 健康检查 |
| 7 个测试类 | PipelineTest, EndToEndTest, DualChannelTest, H2Test, JexlTest, LlmScoringTest, LlmTest |
| ADR-019 维度补齐 | supplementAttrValuesFromDimDefinitions |
| `StandardType.java` 枚举 | STRUCTURED / EXPRESSION 标准类型 |

---

## S3: eval-common 枚举 ✅

**完成时间**: 2026-07-20 | **实际耗时**: ~0.5 小时

### 产出

| 文件 | 说明 |
|------|------|
| `enums/StageType.java` | TOP / NORMAL / LEAF |
| `enums/EventType.java` | RED_LINE / MARK / BONUS / DEDUCT |
| `enums/AggregateMode.java` | weighted_sum / weighted_avg / sum / min / score_accumulate |
| `enums/ScoreMode.java` | RAW_WEIGHT / FIXED / FIXED_WEIGHT / INTERVAL_WEIGHT |
| `enums/AppealType.java` | BONUS / PENALTY / TOTAL / DIMENSION |
| `enums/ErrorCode.java` | (S1 已完成) |
| `Result.java` | (S1 已完成) |
| `exception/EvalException.java` | (S1 已完成) |

---

## S2: 数据库就绪 ✅

**完成时间**: 2026-07-20 | **实际耗时**: ~2 小时

### 产出

- `docs/sql/V003__clean_schema.sql` — 25 张表完整 DDL
- `eval_db` 数据库，基于 `poc-create-20260710.sql` (21张) + 规则引擎 (4张)

### 变更清单

| 项目 | 旧 | 新 |
|------|-----|-----|
| 前缀 | dr_ | eval_ |
| 表数 | 21+4 | 25 |
| domain_code, catagory_code | 有 | 删 |
| tenant_id, data_org_id, main_id | 有 | 删 |
| created_by, updated_by | 有 | 删 |
| enabled, is_active | 有 (CHAR) | 删旧, 加 enabled TINYINT |
| 时区字段 | 有 | 删 |
| id | VARCHAR(20) | BIGINT AUTO_INCREMENT |
| created_at, updated_at | datetime(3) | create_time, update_time |
| 逻辑删除 | 无 | enabled TINYINT DEFAULT 1 (每表) |
| 业务状态 | status VARCHAR (部分表) | 保留 |

### 日志表命名

| 新名 | 旧名 | 含义 |
|------|------|------|
| eval_task_log | dr_evaluation_record_base | 评估任务日志 |
| eval_object_log | dr_index_log_base | 评估对象日志 |
| eval_object_log_msg | dr_index_log_base_msg | 大字段分离 |
| eval_indicator_log | dr_index_log_item | 评估指标日志 |
| eval_indicator_log_msg | dr_index_log_item_msg | 大字段分离 |
| eval_event_log | dr_event_log_item | 评估事件日志 |

### 关键决策

- `enabled` TINYINT: 每张表，逻辑删除
- `status` VARCHAR: 仅工作流表 (eval_model/eval_index/eval_scene/eval_task_log/...)
- msg 表保留: 大字段分离优化，不是 API 日志

---

## S1: 开发环境搭建 ✅

**完成时间**: 2026-07-20 | **实际耗时**: ~2 小时

### 环境

| 项 | 值 |
|----|-----|
| JDK 17 | Temurin 17.0.19 `~/.jdks/temurin-17` |
| JDK 8 | Oracle 1.8.0_77 (，不动) |
| Spring Boot | 3.3.5 |
| MyBatis-Plus | 3.5.7 |
| 包名 | `io.github.accontra.eval` |
| 项目 | `e:/working-brain/eval-system` |
| MySQL | `localhost:3306` db:eval_db |

### 文件

```
eval-system/
├── pom.xml                          # 父 POM + 6 modules
├── setup-env.sh                     # source 之后切换到 JDK 17
├── docs/sql/V003__clean_schema.sql  # 25 张表 DDL
├── eval-common/                     # enums, Result, EvalException
├── eval-domain/                     # (空)
├── eval-infrastructure/             # (空)
├── eval-application/                # (空)
├── eval-api/                        # GlobalExceptionHandler, HealthController
└── eval-boot/                       # EvalApplication, application.yml
```

---

## 会话历史

| 日期 | Session | 内容 |
|------|---------|------|
| 2026-07-20 | S1 ✅ | JDK 17 + Maven 骨架 + 编译 |
| 2026-07-20 | S2 ✅ | 25 张表 clean schema (V003) |
| 2026-07-20 | S3 ✅ | 5 个枚举补完 |
| 2026-07-20 | S4-S9,S11-S13 ✅ | 批量完成: Entity+Mapper+Pipeline+LLM+Handler+JEXL+规则+双通道 |
| 2026-07-20 | S10 ✅ | M1 端到端验证: DeepSeek 76.67分, EvaluationController, curl 全链路通了 |
| 2026-07-20 | S14 ✅ | 双通道对比: DUAL_CHANNEL 并行打分, stats API, 5字段落库 |
