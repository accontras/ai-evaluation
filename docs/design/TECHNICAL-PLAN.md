# AI 评估系统 — 完整技术方案

> **版本**: v1.0 | **日期**: 2026-07-20 | **作者**: accontras
> **基于**: eval-system 知识库全部架构文档、ADR、数据模型、开发笔记、OpenSpec 变更
> **目标**: 基于 Java 17 + Spring Boot 3.3 从零重建独立评估系统

---

## 目录

1. [技术选型](#一技术选型)
2. [系统架构总览](#二系统架构总览)
3. [项目工程结构](#三项目工程结构)
4. [数据模型（28 张表）](#四数据模型28-张表)
5. [Pipeline 与 Handler 设计](#五pipeline-与-handler-设计)
6. [核心领域服务](#六核心领域服务)
7. [异步与消息机制](#七异步与消息机制)
8. [安全与横切关注点](#八安全与横切关注点)
9. [API 接口设计](#九api-接口设计)
10. [实施路线图](#十实施路线图)
11. [从旧系统的迁移要点](#十一从旧系统的迁移要点)

---

## 一、技术选型

### 1.1 运行时

| 选项 | 决策 | 理由 |
|------|------|------|
| JDK 版本 | **Java 17** | Spring Boot 3.x 最低要求，LTS 至 2029，生态最成熟 |
| JDK 发行版 | **Eclipse Temurin 17** | 社区默认，Docker 官方镜像，完全免费 |
| 构建工具 | **Maven 3.9+** | 团队熟悉，与旧系统一致 |

### 1.2 框架与中间件

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.3.x | 最新稳定线，原生支持 Observability |
| Spring Cloud | 2023.0.x | 与 Boot 3.3 对应 |
| MySQL | 8.0+ | 驱动: `mysql-connector-j` (com.mysql.cj.jdbc) |
| RabbitMQ | 3.12+ | 批量评估异步执行 + AI 总结 |
| Redis | 7.0+ | 缓存模型配置 + 分布式锁 |
| MyBatis-Plus | 3.5.x | 团队熟悉，代码生成效率高 |
| JEXL | 3.3.x (Apache Commons) | 表达式求值引擎，安全沙箱 |
| Hutool | 5.8.x | 工具库 |

### 1.3 Java 17 特性应用

| 特性 | 应用场景 | 替代旧写法 |
|------|---------|-----------|
| **Records** | DTO/VO/Context 不可变数据 | `@Data class XxxVO { ... }` |
| **Text Blocks** | SQL 模板、JSON 模板 | 字符串拼接 |
| **Switch 表达式** | Handler 分发、类型路由 | if-else 链 |
| **Sealed Classes** | 事件类型枚举化 | 开放继承 |
| **Pattern Matching** | instanceof + 类型转换 | 两步操作 |
| **Stream.toList()** | 收集不可变列表 | `collect(Collectors.toList())` |

---

## 二、系统架构总览

### 2.1 核心概念模型

```
场景 (Scene) ──→ 模型 (Model) ──→ Stage 树 ──→ 指标 (Index)
                     │                  │
                     │            ┌──────┴──────┐
                     │         top          normal         leaf
                     │        (路由)      (权重聚合)    (算分)
                     │
                  对象 (Target) ──→ 属性 (Attribute) ──→ 取数 (DataPull)
```

### 2.2 系统分层架构

```
Controller        ← 参数校验 + 调 DomainService / Service
  ↓
DomainService     ← 复杂业务编排: Pipeline 执行、深拷贝、申诉重算
  ↓
Service           ← 单表 CRUD: 不包含 if/else 业务判断
  ↓
Mapper            ← 数据访问: MyBatis-Plus BaseMapper
```

| 层 | Module | 规则 |
|----|--------|------|
| Controller | eval-api | 不做业务逻辑，不直接调 Mapper |
| DomainService | eval-application | 跨表事务、Pipeline 编排、深拷贝 |
| Service | eval-domain | **单表 CRUD，不可写 if/else** |
| Mapper | eval-infrastructure | MyBatis-Plus BaseMapper |

> 详细规范见 [eval-system/AGENTS.md](../eval-system/AGENTS.md)

### 2.3 核心哲学：AI 坐主桌，规则引擎当镜子

```
                        评估配置 (领域模型 — 不变)
                       模型 → Stage树 → 指标 → 参考标准
                                   │
              ┌────────────────────┴────────────────────┐
              │           评分通道 (默认并行)             │
              │                                         │
              │  ┌──────────────┐  ┌──────────────┐    │
              │  │  LLM 通道     │  │ 规则引擎通道   │    │
              │  │ (默认)        │  │ (对比基线)     │    │
              │  │ 语义判断      │vs│ JEXL 确定计算  │    │
              │  │ 上下文感知    │  │ 可审计路径     │    │
              │  └──────┬───────┘  └──────┬───────┘    │
              │         └────────┬────────┘             │
              │                  │                      │
              │         ┌────────▼────────┐             │
              │         │  对比 → 仲裁     │             │
              │         │  差异 > 阈值?    │             │
              │         └────────┬────────┘             │
              └──────────────────┼──────────────────────┘
                                 │
              ┌──────────────────▼──────────────────────┐
              │       树聚合 (规则引擎 — 永不替代)        │
              │   weighted_sum / sum / min               │
              │   确定性的数学运算 — 审计底线              │
              └──────────────────┬──────────────────────┘
                                 │
              ┌──────────────────▼──────────────────────┐
              │    事件(LLM异常检测+规则兜底)             │
              │    + 等级映射 + 落库                      │
              └─────────────────────────────────────────┘
```

**分工铁律**:
- **LLM 做**: 语义判断（打分、异常检测、总结）
- **规则引擎做**: 确定性运算（聚合、排名、等级区间）
- **永不混淆**: 不让 LLM 做数学，不让规则引擎做判断

### 2.4 评估执行流程（完整链路）

```
                                    ┌──────────────────────┐
POST /api/v1/evaluation/execute     │  EvaluationController │
  { sceneCode, bizIds, dataPeriod } └──────────┬───────────┘
                                               │
                          ┌────────────────────┴────────────────────┐
                          │  DataPullService.pull()                  │
                          │  路径A: request.data 直传                │
                          │  路径B: GroupViewDataPuller (viewCode)   │
                          │  路径C: MetricDataPuller (兜底)          │
                          │  → Map<bizId, RawData>                  │
                          └────────────────────┬────────────────────┘
                                               │
                    ┌──────────────────────────┴──────────────────────────┐
                    │  ConfigurablePipeline.execute(ctx)                   │
                    │                                                      │
                    │  H1: ValidateAndLoadModel   (加载配置)               │
                    │  H2: FetchIndicatorValues    (提取指标值+维度属性)    │
                    │  H3: CalculateScores         (★双通道: LLM打分∥规则)  │
                    │       ├─ LlmScoringStrategy   (LLM-as-Judge → 分数)  │
                    │       ├─ RuleScoreStrategy    (JEXL并行 → 分数)      │
                    │       ├─ DualChannelCompare   (对比 → diff分级)      │
                    │       ├─ 派生指标求值 (规则引擎)                      │
                    │       ├─ top路由命中 (规则引擎)                       │
                    │       └─ Stage树聚合 (规则引擎 — 审计底线)            │
                    │  H4: EventApplyHandler       (★LLM异常检测∥JEXL规则) │
                    │  H5: AppealAdjustHandler     (申诉重算改分)           │
                    │  H6: SummarizeResultHandler  (等级映射+落库+AI总结)  │
                    └──────────────────────────┬───────────────────────────┘
                                               │
                          ┌────────────────────┴────────────────────┐
                          │  RankingHandler.rank()   (奥运排名)      │
                          │  CallbackNotifyService    (异步回调)     │
                          │  → EvaluationResponse                   │
                          └─────────────────────────────────────────┘
```

### 2.4 异步架构

```
批量评估 (>6 对象):
  Controller → EvalTaskProducer → RabbitMQ → EvalTaskConsumer (6并发)
       │                                          │
       └──────── 返回 batchNo ────────────────────┘ (消费者完成后更新状态)

AI 总结:
  H6 → AiSummaryProducer → RabbitMQ → AiSummaryConsumer
                                         │
                                    LLM 调用 → 回填 summary

申诉重算:
  AppealController.execute() → AppealDomainService
    → 状态更新 + MQ 投递 (独立队列)
    → Consumer 重走完整 Pipeline (isAppealRecalc=true)
```

---

## 三、项目工程结构

```
eval-system-system/
│
├── pom.xml                              ← 父 POM (依赖管理)
│
├── eval-common/                         ← 公共模块
│   └── src/main/java/io/github/accontra/eval/common/
│       ├── enums/                       ← 枚举 (EventType, AggregateMode,
│       │                                     ScoreMode, StageType, AppealType,
│       │                                     Clazz, StandardType 等)
│       ├── constant/                    ← 常量 (ErrorCode, CacheKey 等)
│       ├── exception/                   ← 业务异常 (EvalException,
│       │                                     BizException, 异常码)
│       ├── utils/                       ← 工具类 (ExpressionUtil, JexlUtil)
│       └── dto/                         ← 跨模块共享 DTO (Record)
│
├── eval-domain/                         ← 领域模块
│   └── src/main/java/io/github/accontra/eval/domain/
│       ├── model/                       ← 领域实体
│       │   ├── ModelBase.java
│       │   ├── ModelStage.java          ← parentId + type + level
│       │   ├── ModelIndex.java
│       │   ├── ModelEvent.java          ← targetType + sceneId
│       │   ├── ModelIndexStandard.java  ← standardType + ruleId
│       │   ├── ModelScene.java
│       │   ├── SceneStage.java          ← 方案层维度树 (深拷贝)
│       │   ├── SceneIndex.java
│       │   ├── IndexBase.java           ← calculateType + layer
│       │   ├── IndexStandard.java       ← 通用参考标准
│       │   ├── EvaluationTarget.java
│       │   ├── GradeMapping.java
│       │   ├── AppealHeader.java
│       │   ├── AppealDetail.java
│       │   ├── AppealLog.java
│       │   ├── EvaluationRecordBase.java
│       │   ├── IndexLogBase.java
│       │   ├── IndexLogItem.java
│       │   ├── IndexLogBaseMsg.java
│       │   ├── IndexLogItemMsg.java
│       │   ├── EventLogItem.java
│       │   ├── SceneConfigAudit.java
│       │   ├── SceneConfigSnapshot.java
│       │   ├── DataDecisionRule.java    ← 规则引擎
│       │   ├── DataDecisionScene.java   ← 条件树
│       │   └── DataDimension.java       ← 维度定义
│       │
│       ├── vo/                          ← 值对象
│       │   ├── IndicatorResult.java     ← 5层得分模型
│       │   ├── StageResult.java
│       │   ├── EventResult.java
│       │   ├── EvaluationContext.java   ← Pipeline 数据总线
│       │   └── RawData.java
│       │
│       └── service/                     ← 领域服务接口
│           ├── EvaluationDomainService.java
│           ├── SceneConfigDomainService.java
│           ├── SceneCopyDomainService.java
│           ├── AppealDomainService.java
│           ├── GradeMappingDomainService.java
│           ├── ModelConfigDomainService.java
│           └── RuleExpressionService.java
│
├── eval-infrastructure/                 ← 基础设施模块
│   └── src/main/java/io/github/accontra/eval/infrastructure/
│       ├── mapper/                      ← MyBatis-Plus Mapper
│       │   ├── ModelBaseMapper.java
│       │   ├── ModelStageMapper.java
│       │   ├── ModelIndexMapper.java
│       │   ├── ModelEventMapper.java
│       │   ├── ModelIndexStandardMapper.java
│       │   ├── ModelSceneMapper.java
│       │   ├── SceneStageMapper.java
│       │   ├── SceneIndexMapper.java
│       │   ├── IndexBaseMapper.java
│       │   ├── IndexStandardMapper.java
│       │   ├── EvaluationTargetMapper.java
│       │   ├── GradeMappingMapper.java
│       │   ├── AppealHeaderMapper.java
│       │   ├── AppealDetailMapper.java
│       │   ├── EvaluationRecordBaseMapper.java
│       │   ├── IndexLogBaseMapper.java
│       │   ├── IndexLogItemMapper.java
│       │   ├── EventLogItemMapper.java
│       │   ├── DataDecisionRuleMapper.java
│       │   ├── DataDecisionSceneMapper.java
│       │   └── DataDimensionMapper.java
│       │
│       ├── repository/                  ← Repository 封装
│       ├── mq/                          ← 消息队列
│       │   ├── EvalTaskProducer.java
│       │   ├── EvalTaskConsumer.java
│       │   ├── EvalTaskDeadLetterConsumer.java
│       │   ├── AiSummaryProducer.java
│       │   ├── AiSummaryConsumer.java
│       │   └── config/
│       │       ├── EvalRabbitConfig.java
│       │       └── AiSummaryRabbitConfig.java
│       │
│       ├── datapull/                    ← 数据取数
│       │   ├── DataPullService.java     ← 编排
│       │   ├── GroupViewDataPuller.java ← 路径B
│       │   └── MetricDataPuller.java    ← 路径C (兜底)
│       │
│       ├── cache/                       ← 缓存
│       │   └── ModelCacheService.java   ← Caffeine 本地缓存
│       │
│       ├── callback/                    ← 回调通知
│       │   └── CallbackNotifyService.java
│       │
│       ├── scanner/                     ← 定时扫描
│       │   └── EvalTaskTimeoutScanner.java
│       │
│       └── milestone/                   ← 里程碑记录
│           └── MilestoneRecorder.java
│
├── eval-application/                    ← 应用层 (Pipeline + Handler)
│   └── src/main/java/io/github/accontra/eval/application/
│       ├── pipeline/
│       │   ├── ConfigurablePipeline.java    ← 管道调度器
│       │   └── PipelineConfig.java          ← Handler 注册
│       │
│       ├── handler/
│       │   ├── Handler.java                 ← Handler 接口
│       │   ├── ValidateAndLoadModelHandler.java    (H1)
│       │   ├── FetchIndicatorValuesHandler.java    (H2)
│       │   ├── CalculateScoresHandler.java         (H3)
│       │   ├── EventApplyHandler.java              (H4)
│       │   ├── AppealAdjustHandler.java            (H5)
│       │   ├── SummarizeResultHandler.java         (H6)
│       │   └── RankingHandler.java                 (管道外)
│       │
│       ├── strategy/                     ← 评分策略
│       │   ├── ScoreStrategy.java        ← 策略接口
│       │   ├── RuleScoreStrategy.java    ← 规则评分
│       │   └── AIScoreStrategy.java      ← AI 评分 (MVP 降级)
│       │
│       ├── matcher/                      ← 标准匹配
│       │   ├── StandardMatcher.java
│       │   └── VariableResolver.java     ← 6种变量解析
│       │
│       ├── assembler/                    ← 装配器
│       │   ├── StageNodeAssembler.java   ← Stage 树装配
│       │   └── GradeMappingAssembler.java
│       │
│       └── service/                      ← 应用服务实现
│           ├── EvaluationDomainServiceImpl.java
│           ├── SceneConfigDomainServiceImpl.java
│           ├── SceneCopyDomainServiceImpl.java
│           ├── AppealDomainServiceImpl.java
│           ├── GradeMappingDomainServiceImpl.java
│           └── RuleExpressionServiceImpl.java
│
├── eval-api/                             ← REST API 模块
│   └── src/main/java/io/github/accontra/eval/api/
│       ├── controller/
│       │   ├── EvaluationController.java
│       │   ├── ModelController.java
│       │   ├── SceneController.java
│       │   ├── TargetController.java
│       │   ├── AppealController.java
│       │   └── GradeController.java
│       │
│       ├── request/                      ← 请求 DTO (Record)
│       │   ├── EvaluationRequest.java
│       │   ├── ModelSaveRequest.java
│       │   ├── SceneCreateRequest.java
│       │   ├── AppealSubmitRequest.java
│       │   └── AppealBatchImportRequest.java
│       │
│       ├── response/                     ← 响应 DTO (Record)
│       │   ├── EvaluationResponse.java
│       │   ├── EvaluationProgressResponse.java
│       │   ├── AppealResponse.java
│       │   └── GradeMappingResponse.java
│       │
│       └── config/                       ← Web 配置
│           ├── GlobalExceptionHandler.java
│           └── WebMvcConfig.java
│
├── eval-boot/                            ← 启动模块
│   └── src/main/java/io/github/accontra/eval/
│       ├── EvalApplication.java          ← Spring Boot 入口
│       └── config/
│           ├── MyBatisPlusConfig.java
│           ├── ThreadPoolConfig.java
│           └── RedisConfig.java
│
└── docs/                                 ← 项目文档
    └── sql/
        ├── schema-create.sql             ← 完整建表 (28张表)
        └── seed-data.sql                 ← 种子数据
```

---

## 四、数据模型（28 张表）

### 4.1 表分类

| 分类 | 表名 | 说明 | 处理 |
|------|------|------|------|
| **模型定义** | `eval_model` | 模型主表 | 新建 |
| | `eval_model_stage` | 维度树 (parentId+type+level) | 新建 |
| | `eval_model_index` | 指标关联 | 新建 |
| | `eval_model_event` | 事件配置 | 新建 |
| | `eval_model_standard` | 模型参考标准 | 新建 |
| **指标基础** | `eval_index` | 指标定义 | 新建 |
| | `eval_index_standard` | 通用参考标准 | 新建 |
| **方案** | `eval_scene` | 方案主表 | 新建 |
| | `eval_scene_stage` | 方案维度树 (深拷贝) | 新建 |
| | `eval_scene_index` | 方案指标映射 | 新建 |
| **评估对象** | `eval_target` | 评估对象 | 新建 |
| **维度** | `eval_dimension` | 维度定义 (fieldCode映射) | 新建 |
| **规则引擎** | `eval_decision_rule` | 分支规则 | 新建 |
| | `eval_decision_scene` | 条件树 | 新建 |
| **等级映射** | `eval_grade_mapping` | 分数→等级 | 新建 |
| **申诉** | `eval_appeal_header` | 申诉主表 | 新建 |
| | `eval_appeal_detail` | 申诉明细 | 新建 |
| | `eval_appeal_log` | 申诉操作日志 | 新建 |
| **运行时** | `eval_record` | 评估批次记录 | 新建 |
| | `eval_log` | 评估结果主表 | 新建 |
| | `eval_log_item` | 结果明细 (clazz区分) | 新建 |
| | `eval_log_msg` | 日志消息 | 新建 |
| | `eval_log_item_msg` | 明细消息 | 新建 |
| | `eval_event_log` | 事件触发日志 | 新建 |
| **审计** | `eval_config_audit` | 配置变更审计 | 新建 |
| | `eval_config_snapshot` | 配置快照 | 新建 |
| **断点续算** | `eval_step_snapshot` | 步骤快照 (后期) | 新建 |

> **命名变更**: 去掉 `dr_` 前缀，使用 `eval_` 前缀。表名从旧系统简化。

### 4.2 核心表 DDL（关键字段）

```sql
-- ===== 模型定义层 =====

CREATE TABLE eval_model (
    id          VARCHAR(20)  PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100),
    status      VARCHAR(20)  DEFAULT 'ENABLED',
    aggregate_mode VARCHAR(20) DEFAULT 'weighted_sum',
    dimensions  VARCHAR(500),           -- JSON 数组
    dimension_options VARCHAR(500),
    vn          INT          DEFAULT 1, -- 乐观锁版本号
    created_at  DATETIME,
    updated_at  DATETIME,
    UNIQUE KEY uk_code (code)
);

CREATE TABLE eval_model_stage (
    id          VARCHAR(20)  PRIMARY KEY,
    model_id    VARCHAR(20)  NOT NULL,
    parent_id   VARCHAR(20),            -- 父 stage (树结构)
    type        VARCHAR(10)  DEFAULT 'normal', -- top / normal / leaf
    level       INT          DEFAULT 0,
    code        VARCHAR(50),
    name        VARCHAR(100),
    sn          INT,                    -- 排序
    weight      INT,                    -- 权重
    priority    INT          DEFAULT 0,
    aggregate_mode VARCHAR(20),
    default_score DECIMAL(10,2),
    memo        VARCHAR(500),
    INDEX idx_model_parent (model_id, parent_id)
);

CREATE TABLE eval_model_index (
    id          VARCHAR(20)  PRIMARY KEY,
    model_id    VARCHAR(20)  NOT NULL,
    stage_id    VARCHAR(20)  NOT NULL,
    index_id    VARCHAR(20)  NOT NULL,  -- → eval_index.id
    sn          INT,
    weight      INT,                    -- 废弃，权重归 stage
    score_cap   DECIMAL(10,2),          -- 得分上限
    score_floor DECIMAL(10,2),          -- 得分下限
    query_data_set VARCHAR(100),        -- viewCode
    dimension_options TEXT,             -- 维度映射 JSON
    data_source VARCHAR(100),
    memo        VARCHAR(500)
);

CREATE TABLE eval_model_event (
    id          VARCHAR(20)  PRIMARY KEY,
    model_id    VARCHAR(20),
    scene_id    VARCHAR(20),            -- NULL=模板层
    code        VARCHAR(50),
    name        VARCHAR(100),
    event_type  VARCHAR(20)  NOT NULL,  -- RED_LINE/BONUS/PENALTY/EVENT_SCORE
    dimension_rule TEXT,                -- JEXL 触发条件
    score_expression VARCHAR(200),
    priority    INT          DEFAULT 0,
    red_line_message VARCHAR(500),
    target_type VARCHAR(10)  DEFAULT 'MODEL',
    target_id   VARCHAR(20),
    rule_id     VARCHAR(20),            -- → eval_decision_rule.id
    INDEX idx_model_target (model_id, target_type, target_id),
    INDEX idx_rule (rule_id)
);

CREATE TABLE eval_model_standard (
    id          VARCHAR(20)  PRIMARY KEY,
    model_id    VARCHAR(20),
    stage_id    VARCHAR(20),
    index_id    VARCHAR(20),
    scene_id    VARCHAR(20),            -- NULL=模板层
    target_type VARCHAR(10)  DEFAULT 'STAGE',
    target_id   VARCHAR(20),
    code        VARCHAR(50),
    dimension_rule TEXT,                -- JEXL 运行时快照
    min_value   DECIMAL(10,2),
    max_value   DECIMAL(10,2),
    dict_value  VARCHAR(100),
    score       DECIMAL(10,2),
    score_mode  VARCHAR(30)  DEFAULT 'RAW_WEIGHT',
    standard_type VARCHAR(20) DEFAULT 'STRUCTURED', -- STRUCTURED/EXPRESSION
    priority    INT,
    rule_id     VARCHAR(20),            -- → eval_decision_rule.id
    enabled     TINYINT      DEFAULT 1,
    INDEX idx_scene (scene_id),
    INDEX idx_target (target_type, target_id),
    INDEX idx_rule (rule_id)
);

-- ===== 指标基础层 =====

CREATE TABLE eval_index (
    id          VARCHAR(20)  PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100),
    catalog     VARCHAR(50),
    unit        VARCHAR(20),
    calculate_type VARCHAR(100),         -- BASIC / DERIVED
    calculate_rule TEXT,                 -- JEXL 表达式 (派生指标)
    relate_index VARCHAR(500),           -- 依赖指标编码 (逗号分隔)
    layer       INT          DEFAULT 0, -- 拓扑层级 (保存时预计算)
    dimensions  VARCHAR(500),            -- JSON 数组
    index_field_code VARCHAR(255),       -- 指标字段编码
    query_data_set TEXT,                 -- data-view 定义
    dimension_options TEXT,
    filter_options TEXT,
    status      VARCHAR(20)  DEFAULT 'ENABLED',
    UNIQUE KEY uk_code (code)
);

-- ===== 方案层 (深拷贝隔离) =====

CREATE TABLE eval_scene (
    id          VARCHAR(20)  PRIMARY KEY,
    code        VARCHAR(60)  NOT NULL,
    model_id    VARCHAR(20)  NOT NULL,
    name        VARCHAR(100),
    target_code VARCHAR(100),            -- 评估对象 code
    aggregate_mode VARCHAR(20),
    status      VARCHAR(20)  DEFAULT 'DRAFT',
    appeal_window_days INT,
    grade_mapping_mode VARCHAR(20) DEFAULT 'SCORE_RANGE',
    grade_config TEXT,
    default_route_branch VARCHAR(20),
    evaluate_mode VARCHAR(10),
    callback_api VARCHAR(120),
    callback_token VARCHAR(128),
    callback_body_template TEXT,
    red_line_type INT DEFAULT 0,
    rank_range VARCHAR(20) DEFAULT 'all',
    rank_type VARCHAR(10) DEFAULT 'ASC',
    default_event_score INT DEFAULT 0,
    vn INT DEFAULT 1,
    UNIQUE KEY uk_code (code)
);

CREATE TABLE eval_scene_stage (
    id          VARCHAR(20)  PRIMARY KEY,
    scene_id    VARCHAR(20)  NOT NULL,
    source_id   VARCHAR(20),            -- 模板层原 ID
    parent_id   VARCHAR(20),
    type        VARCHAR(10)  DEFAULT 'normal',
    level       INT          DEFAULT 0,
    code        VARCHAR(50),
    name        VARCHAR(100),
    sn          INT,
    weight      INT,
    priority    INT,
    aggregate_mode VARCHAR(20),
    default_score DECIMAL(10,2),
    memo        VARCHAR(500),
    INDEX idx_scene_parent (scene_id, parent_id),
    INDEX idx_source (source_id)
);

CREATE TABLE eval_scene_index (
    id          VARCHAR(20)  PRIMARY KEY,
    scene_id    VARCHAR(20)  NOT NULL,
    stage_id    VARCHAR(20),
    source_id   VARCHAR(20),            -- 模板层原 ID
    index_code  VARCHAR(50),
    index_name  VARCHAR(100),
    query_data_set VARCHAR(100),
    dimension_options TEXT,
    INDEX idx_scene_stage (scene_id, stage_id)
);

-- ===== 运行时表 =====

CREATE TABLE eval_record (
    id          VARCHAR(20)  PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    scene_code  VARCHAR(64),
    model_code  VARCHAR(50),
    eval_period VARCHAR(20),
    status      VARCHAR(20)  DEFAULT 'PENDING', -- RUNNING/SUCCESS/FAILED
    biz_status  VARCHAR(16)  DEFAULT 'DRAFT',   -- DRAFT/PUBLIC/ARCHIVED
    start_time  DATETIME,
    end_time    DATETIME,
    query_condition TEXT,
    trace_no    VARCHAR(50),
    UNIQUE KEY uk_code (code),
    INDEX idx_scene_period_status (scene_code, eval_period, status)
);

CREATE TABLE eval_log (
    id          VARCHAR(20)  PRIMARY KEY,
    record_id   VARCHAR(20)  NOT NULL,
    scene_code  VARCHAR(64),
    model_code  VARCHAR(50),
    target_code VARCHAR(50),
    total_score DECIMAL(10,2),
    risk_level  VARCHAR(20),
    grade       VARCHAR(4),             -- S/A/B/C/D/E
    grade_mapping_mode VARCHAR(20),
    appeal_adjusted_score DECIMAL(10,2),
    evidence_chain TEXT,
    summary     VARCHAR(500),
    summary_status VARCHAR(20) DEFAULT 'PENDING',
    rank        INT,
    rank_total  INT,
    worker_id   VARCHAR(255),
    eval_period VARCHAR(100),
    status      VARCHAR(20),
    INDEX idx_record (record_id),
    INDEX idx_scene (scene_code),
    INDEX idx_scene_grade (scene_code, grade)
);

CREATE TABLE eval_log_item (
    id          VARCHAR(20)  PRIMARY KEY,
    log_id      VARCHAR(20)  NOT NULL,
    record_id   VARCHAR(20),
    clazz       VARCHAR(20),            -- INDEX / STAGE / EVENT / APPEAL
    stage_code  VARCHAR(100),
    index_code  VARCHAR(100),
    index_name  VARCHAR(100),
    sn          INT,
    weight      INT,
    interval_weight DECIMAL(10,4),
    data_value  VARCHAR(255),
    score       DECIMAL(10,2),
    standard_score DECIMAL(10,2),
    stage_score DECIMAL(10,4),
    score_mode  VARCHAR(32),
    dimension_rule TEXT,
    is_red_line TINYINT,
    priority    INT,
    status      VARCHAR(20),
    evaluate_instance VARCHAR(50),
    INDEX idx_log (log_id),
    INDEX idx_record (record_id)
);

CREATE TABLE eval_event_log (
    id          VARCHAR(20)  PRIMARY KEY,
    log_id      VARCHAR(20)  NOT NULL,
    record_id   VARCHAR(20),
    biz_id      VARCHAR(50),
    scene_code  VARCHAR(64),
    model_code  VARCHAR(50),
    event_code  VARCHAR(50),
    event_name  VARCHAR(100),
    event_type  VARCHAR(20),            -- RED_LINE/MARK/BONUS/DEDUCT
    dimension_rule TEXT,
    score_before DECIMAL(10,2),
    score_after  DECIMAL(10,2),
    event_score  DECIMAL(10,2),
    red_line_message VARCHAR(500),
    trigger_values TEXT,
    is_red_line CHAR(1) DEFAULT 'N',
    status      VARCHAR(20),
    INDEX idx_log (log_id),
    INDEX idx_record (record_id),
    INDEX idx_event_type (event_type)
);

-- ===== 申诉 =====

CREATE TABLE eval_appeal_header (
    id          VARCHAR(20)  PRIMARY KEY,
    appeal_no   VARCHAR(32)  NOT NULL,
    appeal_type VARCHAR(20)  NOT NULL,  -- BONUS/PENALTY/TOTAL/DIMENSION
    scene_id    VARCHAR(20)  NOT NULL,
    object_id   VARCHAR(20)  NOT NULL,
    data_period VARCHAR(50),
    score_adjustment DECIMAL(10,2),
    adjusted_total_score DECIMAL(10,2),
    reason      TEXT,
    status      VARCHAR(20)  DEFAULT 'PENDING',
    batch_no    VARCHAR(32),
    eval_record_id VARCHAR(32),
    UNIQUE KEY uk_appeal_no (appeal_no),
    INDEX idx_scene_status (scene_id, status),
    INDEX idx_object (object_id)
);

CREATE TABLE eval_appeal_detail (
    id          VARCHAR(20)  PRIMARY KEY,
    appeal_id   VARCHAR(20)  NOT NULL,
    object_id   VARCHAR(20)  NOT NULL,
    dimension_id VARCHAR(20),
    score_adjustment DECIMAL(10,2),
    reason      TEXT,
    INDEX idx_appeal (appeal_id)
);

-- ===== 等级映射 =====

CREATE TABLE eval_grade_mapping (
    id          VARCHAR(20)  PRIMARY KEY,
    scene_id    VARCHAR(20)  NOT NULL,
    grade       VARCHAR(4)   NOT NULL,   -- S/A/B/C/D/E
    lower_bound DECIMAL(10,2),
    upper_bound DECIMAL(10,2),
    priority    INT DEFAULT 0,
    memo        VARCHAR(100),
    INDEX idx_scene (scene_id)
);

-- ===== 规则引擎 =====

CREATE TABLE eval_decision_rule (
    id          VARCHAR(20)  PRIMARY KEY,
    code        VARCHAR(50),
    name        VARCHAR(100),
    dimension_code VARCHAR(50),
    type        VARCHAR(20),
    enabled     TINYINT DEFAULT 1,
    is_active   TINYINT DEFAULT 1,
    vn          INT DEFAULT 1,
    status      VARCHAR(20)
);

CREATE TABLE eval_decision_scene (
    id          VARCHAR(20)  PRIMARY KEY,
    rule_id     VARCHAR(20)  NOT NULL,
    code        VARCHAR(50),
    name        VARCHAR(100),
    parent_id   VARCHAR(20),
    level_no    INT,
    sn          INT,
    dimension_code VARCHAR(50),
    operator    VARCHAR(20),
    data_value  VARCHAR(255),
    dimension_rule VARCHAR(500),
    INDEX idx_rule (rule_id)
);

CREATE TABLE eval_dimension (
    id          VARCHAR(20)  PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100),
    field_code  VARCHAR(255),            -- 映射桥梁
    data_type   VARCHAR(50),
    status      VARCHAR(20),
    UNIQUE KEY uk_code (code),
    INDEX idx_field_code (field_code)
);
```

---

## 五、Pipeline 与 Handler 设计

### 5.1 Handler 接口 (Java 17)

```java
// eval-application/src/main/java/.../handler/Handler.java
public interface Handler {
    /** 步骤编码 */
    String stepCode();
    /** 步骤名称 */
    String stepName();
    /** 执行 */
    void execute(EvaluationContext ctx);
    /** 是否跳过 */
    default boolean shouldSkip(EvaluationContext ctx) { return false; }
    /** 执行顺序 (越小越先) */
    int order();
}
```

### 5.2 EvaluationContext (核心数据总线)

```java
public class EvaluationContext {
    // ===== 输入 =====
    String sceneCode;
    String bizId;
    String dataPeriod;
    Long recordId;
    Map<String, Object> rawData;
    boolean isAppealRecalc;

    // ===== H1 产出 =====
    ModelScene scene;
    ModelBase model;
    List<StageNode> stageTree;        // 装配后的 stage 树/森林
    List<ModelIndex> modelIndices;
    List<ModelIndexStandard> modelStandards;
    List<ModelEventVo> modelEvents;
    EvaluationTarget target;
    Map<String, DataDimension> dimDefinitions;
    Map<String, IndexBase> indexBaseMap;
    List<GradeMapping> gradeMappings;

    // ===== H2 产出 =====
    Map<String, Object> rawValues;
    Map<String, Object> attrValues;

    // ===== H3 产出 =====
    List<StageResult> stageResults;
    BigDecimal totalScore;

    // ===== H4 产出 =====
    List<EventResult> triggeredEvents;
    BigDecimal adjustedTotalScore;
    boolean blocked;

    // ===== H5 产出 =====
    BigDecimal appealAdjustedScore;

    // ===== H6 产出 =====
    String grade;
    String riskLevel;
    String summary;
    EvaluationResponse response;
}
```

### 5.3 Pipeline 调度器

```java
@Component
public class ConfigurablePipeline {
    private final List<Handler> handlers;

    public ConfigurablePipeline(List<Handler> handlers) {
        // 固定序：按 order 排序（等同于 init() 显式声明）
        this.handlers = handlers.stream()
            .sorted(Comparator.comparingInt(Handler::order))
            .toList();
    }

    public void execute(EvaluationContext ctx) {
        for (Handler handler : handlers) {
            if (handler.shouldSkip(ctx)) continue;
            try {
                handler.execute(ctx);
            } catch (Exception e) {
                log.error("[{}] 执行失败: {}", handler.stepName(), e.getMessage(), e);
                throw new EvalException(handler.stepCode(), e);
            }
        }
    }
}
```

### 5.4 六个 Handler 详细设计

#### H1: ValidateAndLoadModelHandler (order=1)

```
职责: 校验入参 → 加载全部配置到 Context
输入: ctx.sceneCode, ctx.bizId
输出: scene, model, stageTree, modelIndices, modelStandards,
      modelEvents, target, dimDefinitions, indexBaseMap, gradeMappings

加载链路 (9 次查询):
  scene → model → stages (装配树) → indices → standards
  → events → target → dimDefinitions → indexBaseMap → gradeMappings

异常码: E001(场景不存在) E002(场景禁用) E003(模型不存在)
        E004(模型无指标) E005(对象不存在)
```

#### H2: FetchIndicatorValuesHandler (order=2)

```
职责: 纯内存操作 — 从 rawData 提取指标值和维度属性值
输入: ctx.rawData, ctx.modelIndices, ctx.indexBaseMap, ctx.dimDefinitions
输出: ctx.rawValues, ctx.attrValues

关键逻辑:
  1. 遍历 modelIndices，获取 indexBase
  2. 通过 dimensions[0] 找到主维度 → fieldCode → rawData.fields[fieldCode] → rawValues
  3. 通过 dimensions[0..N] 找到所有维度 → fieldCode → rawData.fields[fieldCode] → attrValues
  4. supplementAttrValuesFromDimDefinitions() 补齐未映射的维度属性 (ADR-019)
```

#### H3: CalculateScoresHandler (order=3)

```
职责: 核心计算引擎 — 派生求值 + 路由命中 + stage 树自底向上算分

执行流程:
  Step 0: 派生指标求值
    ├─ 过滤 calculateType=DERIVED 的指标
    ├─ 按 layer 升序排序
    └─ 逐条 JEXL 求值 → 写入 rawValues

  Step 1: 判断顶层结构
    ├─ 多根 top → 森林路由模式
    │   对每个 top 根: 评估路由表达式 → 命中唯一子分支 → 递归算子树
    ├─ 单根 top → 单树路由模式
    └─ 无 top → 直接算整棵树

  Step 2: 树算分 (自底向上)
    ├─ 按 level 倒序排列所有 stage
    ├─ leaf: applyLeafScoringRule()
    │   ├─ 查参考标准 (targetType=STAGE)
    │   ├─ 按 standardType 评估条件 (STRUCTURED → 区间匹配, EXPRESSION → JEXL)
    │   ├─ 按 scoreMode 计算得分 (INTERVAL_WEIGHT / FIXED / FIXED_WEIGHT / RAW_WEIGHT)
    │   └─ 5层模型: rawValue → standardScore → rawScore → weightedScore → stageScore
    ├─ normal: aggregateChildren()
    └─ top: routeAndCalculate()

6种变量解析:
  ${val}           → 当前指标原始值
  ${weight}        → 区间权重
  ${attr.xxx}      → 维度属性 (按名称)
  ${dim.xxx}       → 维度属性 (按编码)
  ${idx.xxx.value} → 跨指标原始值
  ${idx.xxx.score} → 跨指标得分

聚合模式: weighted_avg / weighted_sum / sum / min / score_accumulate
Fallback: stage.aggregateMode → model.aggregateMode → "weighted_sum"
```

#### H4: EventApplyHandler (order=4)

```
职责: 事件触发与得分调整
输入: ctx.stageResults, ctx.modelEvents, ctx.attrValues
输出: ctx.triggeredEvents, ctx.adjustedTotalScore, ctx.blocked

执行逻辑:
  1. 过滤 targetType=MODEL 的事件
  2. 按 priority 排序
  3. 逐条评估 dimension_rule (JEXL)
  4. 命中后按 eventType 执行:
     RED_LINE     → totalScore = 0, blocked = true
     BONUS        → totalScore += scoreValue (来自 scoreExpression)
     PENALTY/DEDUCT → totalScore -= scoreValue
     EVENT_SCORE  → totalScore += scoreValue
  5. 写入 ctx.triggeredEvents
```

#### H5: AppealAdjustHandler (order=5)

```
职责: 申诉重算时调整得分 (条件触发)
触发条件: ctx.isAppealRecalc = true
跳过条件: H4 中 blocked = true

执行逻辑:
  1. 查 eval_appeal_header WHERE scene_id + object_id + status=PENDING
  2. 查 eval_appeal_detail WHERE appeal_id
  3. 按 appealType 调整:
     BONUS   → totalScore += adjustment
     PENALTY → totalScore -= adjustment
     TOTAL   → totalScore = adjustment (直接替换)
  4. 写 ctx.appealAdjustedScore
```

#### H6: SummarizeResultHandler (order=6)

```
职责: 等级映射 + 落库 + 证据链 + AI 总结触发
输入: ctx 全部数据
输出: ctx.response, 数据库日志记录

执行逻辑:
  Step 1: 确定最终总分
    → 优先 appealAdjustedScore，否则 totalScore

  Step 2: 等级映射 (SCORE_RANGE)
    → O(n) 查 eval_grade_mapping，按 [lower, upper] 区间命中

  Step 3: 风险等级
    → redLine 或 totalScore < 60 → HIGH
    → 60 ≤ totalScore < 80 → MEDIUM
    → totalScore ≥ 80 → LOW

  Step 4: 落库
    → eval_log (1条)
    → eval_log_item (N条, clazz=INDEX/STAGE/EVENT)
    → eval_event_log (M条)

  Step 5: 证据链
    → JSON 构建 → eval_log.evidence_chain

  Step 6: AI 总结触发
    → AiSummaryProducer.send(logId)
    → eval_log.summary_status = PENDING
```

#### RankingHandler (管道外)

```
职责: 跨对象排序 → 奥运排名 → 写回
触发: 由 EvaluationDomainService 在 Pipeline 后显式调用
条件: bizIds.size() > 1 且 scene.options.rankingEnabled

排名算法: 奥运排名 (1,1,3,4)
  同分→同名，后续跳过重复数
```

### 5.5 Stage 树数据结构

```java
// Stage 树节点 (装配后)
public class StageNode {
    String id;
    String code;
    String name;
    StageType type;          // TOP / NORMAL / LEAF
    int level;
    int weight;
    int priority;
    int sn;
    AggregateMode aggregateMode;
    BigDecimal defaultScore;

    StageNode parent;
    List<StageNode> children;     // 子节点
    List<ModelIndex> indices;     // LEAF 层挂载的指标
    List<ModelIndexStandard> standards; // 关联的参考标准
}
```

---

## 六、核心领域服务

### 6.1 服务清单

| 领域服务 | 职责 | 关键方法 |
|---------|------|---------|
| `EvaluationDomainService` | 评估执行编排 | `execute()`, `executeBatch()` |
| `ModelConfigDomainService` | 模型配置管理 | `saveModel()`, `cascadeSaveStages()` |
| `SceneConfigDomainService` | 方案配置管理 | `createScene()`, `publishScene()` |
| `SceneCopyDomainService` | 深拷贝 | `deepCopy()` (模板→方案) |
| `AppealDomainService` | 申诉管理 | `submitAppeal()`, `batchImport()`, `executeAppeal()` |
| `GradeMappingDomainService` | 等级映射 | `saveGradeMapping()`, `resolveGrade()` |
| `RuleExpressionService` | 规则表达式 | `generateJexl()`, `evaluate()` |

### 6.2 深拷贝流程 (SceneCopyDomainService)

```
createSceneFromModel(modelId, targetCode)
  │
  ├── 1. 创建方案主表 eval_scene
  │
  ├── 2. 深拷贝 stage 树
  │      INSERT eval_scene_stage SELECT ... FROM eval_model_stage
  │      重写 id → 同时记录 source_id
  │      维护 parent_id (映射为新 id)
  │
  ├── 3. 深拷贝指标
  │      INSERT eval_scene_index SELECT ... FROM eval_model_index
  │
  ├── 4. 深拷贝事件 (同表 INSERT, scene_id=方案ID)
  │      INSERT eval_model_event (scene_id=newSceneId) ...
  │
  ├── 5. 深拷贝标准 (同表 INSERT, scene_id=方案ID)
  │      INSERT eval_model_standard (scene_id=newSceneId) ...
  │
  └── 6. 复制等级映射默认值
         INSERT eval_grade_mapping DEFAULT VALUES ...
```

---

## 七、异步与消息机制

### 7.1 MQ 拓扑

```
Exchange: eval.task.exchange (topic)
  Queue: eval.task.queue        → EvalTaskConsumer (6 并发)
  Queue: eval.task.dlq          → EvalTaskDeadLetterConsumer

Exchange: eval.ai-summary.exchange (topic)
  Queue: eval.ai-summary.queue  → AiSummaryConsumer

配置:
  - Publisher Confirm: 异步确认 + 10s 超时 + 1 次重试
  - Prefetch: eval 队列=1, ai-summary 队列=5
  - 连接: 共享 ConnectionFactory，独立 RabbitTemplate
```

### 7.2 批量评估模式

```
bizIds.length = 1  → 同步返回 (直接 Pipeline)
bizIds.length ≤ 6  → 线程池并行
bizIds.length > 6  → MQ 异步

Controller:
  ├─ DataPull (单次, 所有对象) → Map<bizId, RawData>
  ├─ 判断同步/异步
  ├─ 同步: foreach → Pipeline → 返回结果
  └─ 异步: foreach → EvalTaskProducer.send() → 返回 batchNo

Consumer:
  ├─ 接收消息 { bizId, rawData, sceneCode, recordId }
  ├─ 重建 EvaluationContext
  ├─ Pipeline.execute(ctx)
  ├─ 更新 eval_record 进度
  └─ 最后一个完成 → 触发 RankingHandler + Callback
```

### 7.3 超时扫描器

```
EvalTaskTimeoutScanner
  ├─ 每 60s 扫描
  ├─ 查询 eval_record WHERE status='RUNNING' AND start_time < NOW() - 5min
  ├─ 标记为 TIMEOUT
  └─ 重置 PENDING 状态的 log 记录 (供补偿)
```

### 7.4 幂等机制

| 场景 | 机制 |
|------|------|
| 评估执行 | `(scene_code, eval_period, status)` 唯一索引 |
| AI 总结 | CAS: `UPDATE eval_log SET summary_status='RUNNING' WHERE summary_status='PENDING'` |
| 申诉重算 | `checkNoRunningAppealReEvaluation(sourceRecordId)` |
| 排名回写 | RankingHandler 本身幂等 (同分同名) |

---

## 八、安全与横切关注点

### 8.1 安全

| 关注点 | 方案 |
|--------|------|
| 回调认证 | `callback_token` → Header: `x-api-token` |
| JEXL 沙箱 | Apache Commons JEXL 默认禁用反射/类加载 |
| SQL 注入 | MyBatis-Plus 参数化查询 (#{}, 不用 ${}) |
| 配置外化 | Nacos/application.yml，禁止硬编码 |

### 8.2 事务

| 场景 | 事务边界 |
|------|---------|
| 模型配置保存 | `ModelConfigDomainService` @Transactional |
| 方案深拷贝 | `SceneConfigDomainService` @Transactional |
| 评估单对象落库 | H6 内 @Transactional (log + log_item + event_log) |
| 申诉执行 | `AppealDomainService` @Transactional (状态 + MQ) |
| Pipeline 内 | 无 @Transactional (Handler 间独立，单对象失败不影响其他) |

### 8.3 缓存

```
ModelCacheService (Caffeine 本地缓存)
  ├─ 缓存对象: 模型配置 (scene → model → stages → indices → standards)
  ├─ TTL: 5 分钟
  └─ 失效: 配置发布后 version(vn) 变更 → 自动失效

远期考虑: Redis 分布式缓存 (多节点场景)
```

### 8.4 日志规范

```
[Controller]  ← 请求/响应
[Handler1] ~ [Handler6] ← Handler 步骤日志
[Pipeline]   ← 管道调度日志
[DataPull]   ← 数据取数日志
[EvalTask]   ← MQ 生产者/消费者
[AiSummary]  ← AI 总结
[Ranking]    ← 排名
[Appeal]     ← 申诉
[Callback]   ← 回调通知
[Milestone]  ← 里程碑
```

---

## 九、API 接口设计

### 9.1 评估执行

```
POST /api/v1/evaluation/execute
Request: {
  "sceneCode": "xxx",
  "bizIds": ["biz1"],        // 1个 → 同步, >1个 → 异步
  "dataPeriod": "2026-Q2",
  "data": {...}              // 可选: 路径A 直传数据
}
Response (同步): {
  "bizId": "biz1",
  "totalScore": 85.5,
  "grade": "A",
  "riskLevel": "LOW",
  "stageResults": [...],
  "triggeredEvents": [...]
}
Response (异步): {
  "batchNo": "xxx",
  "bizCount": 500,
  "status": "RUNNING"
}
```

### 9.2 评估进度

```
GET /api/v1/evaluation/progress/{batchNo}
Response: {
  "batchNo": "xxx",
  "total": 500,
  "completed": 320,
  "failed": 2,
  "status": "RUNNING",      // PENDING / RUNNING / SUCCESS / FAILED / PARTIAL_FAIL
  "estimatedRemaining": "30s"
}
```

### 9.3 模型配置

```
POST   /api/v1/model                    ← 创建模型
PUT    /api/v1/model/{id}               ← 更新模型
GET    /api/v1/model/{id}               ← 查询模型详情 (含 stage 树)
DELETE /api/v1/model/{id}               ← 删除模型
POST   /api/v1/model/{id}/stage         ← 新增 stage 节点
PUT    /api/v1/model/{id}/stage/{sid}   ← 更新 stage
DELETE /api/v1/model/{id}/stage/{sid}   ← 删除 stage
PUT    /api/v1/model/{id}/stage/sort    ← 拖拽排序
```

### 9.4 方案管理

```
POST /api/v1/scene/create               ← 从模型深拷贝创建方案
  Request: { "modelId": "xxx", "targetCode": "xxx" }
PUT  /api/v1/scene/{id}/publish         ← 发布方案
GET  /api/v1/scene/{id}                 ← 查询方案 (含完整 stage 树)
```

### 9.5 申诉

```
POST /api/v1/appeal/submit              ← 提交申诉
POST /api/v1/appeal/batch-import        ← 批量导入 (Excel)
POST /api/v1/appeal/execute/{id}        ← 执行重算
GET  /api/v1/appeal/{id}                ← 查询申诉详情
GET  /api/v1/appeal/list                ← 申诉列表
```

### 9.6 评价查询

```
GET /api/v1/evaluation/results          ← 评估结果列表 (分页)
GET /api/v1/evaluation/result/{logId}   ← 评估详情 (含指标明细 + 事件)
GET /api/v1/evaluation/result/{logId}/evidence ← 证据链 (trace)
```

---

## 十、实施路线图

> **详细开发计划**: 见 [DEVELOPMENT-PLAN.md](DEVELOPMENT-PLAN.md) — 45 个 session，每个 2-3 小时，面向业余时间开发。
> 本节为架构层面的路线图概述。

### 核心原则

1. **先跑通规则引擎，再做 AI 集成** — M1-M2 用规则引擎打通全链路，M3 集中做 AI 双通道
2. **不做 DAG** — 线性 Pipeline 已验证有效，DAG 留给 v2.0
3. **每个里程碑可验证** — 不攒代码，每个阶段都有可运行的产物

### 五大里程碑

```
M0: 空项目跑通                    [Session 1-2]    ← 即 S1-S2
  │   验证: /actuator/health → UP
  │
M1: 单对象规则评估跑通              [Session 3-12]   ← 即 S3-S12
  │   验证: curl POST /evaluation/execute → totalScore
  │   产出: Pipeline(H1→H2→H3→H6) + 5张核心表 + DataPull路径A
  │
M2: 批量异步 + 配置管理              [Session 13-22]  ← 即 S13-S22
  │   验证: 100对象批量评估全部落库
  │   产出: Stage树算分 + 事件 + 派生 + 路径B + MQ异步 + 深拷贝
  │
M3: ★ AI 双通道上线                 [Session 23-32]  ← 即 S23-S32
  │   验证: 规则评分 vs LLM评分对比数据 ≥ 10条
  │   产出: LLM评分策略 + AI总结 + 多模型对比 + NL→JEXL实验 + RAG实验
  │        ★ 这是整个项目的核心差异化
  │
M4: 申诉 + 等级 + 运维              [Session 33-42]  ← 即 S33-S42
  │   验证: docker compose up → 完整流程可跑
  │   产出: 申诉体系 + 等级映射 + 排名回调 + 缓存 + Docker
  │
M5: 开源发布就绪                    [Session 43-45]  ← 即 S43-S45
      验证: Gitee/GitHub 仓库 + Demo + 博客
```

### 时间估算

| 部分 | Session 数 | 业余预估 |
|------|-----------|---------|
| 地基 (M0-M1) | 12 | ~3 周 |
| 完善 (M2) | 10 | ~2.5 周 |
| ★ AI 集成 (M3) | 10 | ~2.5 周 |
| 收尾 (M4) | 10 | ~2.5 周 |
| 发布 (M5) | 3 | ~1 周 |
| **总计** | **45** | **~3 个月** |

### 不做的事情（v1.0 范围外）

| 项目 | 原因 | 状态 |
|------|------|------|
| DAG 编排 | 当下评估流程纯线性，无并行分支 | → v2.0 |
| 指标级 AI 评分 (`score_mode=AI`) | 实现成本高，但对比数据更有价值 | → v2.0 (先做对比) |
| 断点续算 | 评估耗时可控 (<30s/对象)，暂不需要 | → v2.0 |
| 数据飞轮闭环 | 需要足够历史数据积累 | → v3.0 |
| 多租户 | 个人项目不需要 | → 永不 |

---

## 十一、从旧系统的迁移要点

### 11.1 包名迁移

```
旧: javax.persistence.* / javax.annotation.*
新: jakarta.persistence.* / jakarta.annotation.*

旧: com.mysql.jdbc.Driver
新: com.mysql.cj.jdbc.Driver

旧: spring.factories (META-INF/)
新: org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 11.2 Java 17 代码改进清单

| 位置 | 旧代码 (Java 8) | 新代码 (Java 17) |
|------|----------------|-------------------|
| 异常码 | `public static final String ERR_001 = "AE01"` | `enum ErrorCode { AE01("场景不存在") }` |
| 聚合模式 | `if ("weighted_sum".equals(mode))` | `switch (aggregateMode) { case WEIGHTED_SUM -> ... }` |
| VO/DTO | `@Data class EvalResult { get/set... }` | `record EvalResult(...) { }` |
| 指标结果 | `new IndicatorResult(); r.setRawValue(v)` | `new IndicatorResult(rawValue, standardScore, ...)` |
| 变量解析 | `if (var.startsWith("${attr."))` | Pattern Matching `instanceof AttrVar v` |
| 字符串构建 | `"select * from " + table` | Text Block `"""select * from ${table}"""` |
| Handler 排序 | `@Order(1)` 注解 | `int order()` 接口方法 |
| 列表转换 | `.collect(Collectors.toList())` | `.toList()` |

### 11.3 数据库表名对照

| 旧表 (dr_ 前缀) | 新表 (eval_ 前缀) | 说明 |
|-----------------|-------------------|------|
| `dr_model_base` | `eval_model` | 简化 |
| `dr_model_stage` | `eval_model_stage` | — |
| `dr_model_index` | `eval_model_index` | — |
| `dr_model_event` | `eval_model_event` | — |
| `dr_model_index_standard` | `eval_model_standard` | 简化 |
| `dr_index_base` | `eval_index` | 简化 |
| `dr_index_standard` | `eval_index_standard` | — |
| `dr_model_scene` | `eval_scene` | — |
| `dr_model_scene_stage` | `eval_scene_stage` | — |
| `dr_model_scene_index` | `eval_scene_index` | — |
| `dr_evaluation_target` | `eval_target` | 简化 |
| `dr_data_dimension` | `eval_dimension` | 简化 |
| `dr_data_decision_rule` | `eval_decision_rule` | — |
| `dr_data_decision_scene` | `eval_decision_scene` | — |
| `dr_grade_mapping` | `eval_grade_mapping` | — |
| `dr_appeal_header` | `eval_appeal_header` | — |
| `dr_appeal_detail` | `eval_appeal_detail` | — |
| `dr_appeal_log` | `eval_appeal_log` | — |
| `dr_evaluation_record_base` | `eval_record` | 简化 |
| `dr_index_log_base` | `eval_log` | 简化 |
| `dr_index_log_item` | `eval_log_item` | — |
| `dr_index_log_base_msg` | `eval_log_msg` | 简化 |
| `dr_index_log_item_msg` | `eval_log_item_msg` | — |
| `dr_event_log_item` | `eval_event_log` | 简化 |
| `dr_scene_config_audit` | `eval_config_audit` | — |
| `dr_scene_config_snapshot` | `eval_config_snapshot` | — |
| `dr_evaluation_step_snapshot` | `eval_step_snapshot` | — |

### 11.4 不复用的旧系统组件

以下旧系统的组件**不迁移**，在新系统中重新实现或替换：

| 组件 | 原因 | 新方案 |
|------|------|--------|
| `zi18n-common` 公共包 | 与旧系统耦合 | `eval-common` 自建 |
| `zi18n-data-rule-api` | 项目耦合 | `eval-api` 独立 |
| `zi18n-data-rule-service` | 项目耦合 | `eval-application` 独立 |
| `io.github.accontra.eval.i18n.rule.*` 包名 | 旧命名空间 | `io.github.accontra.eval.*` |
|  `BaseController/BaseService` | 框架依赖 | Spring Boot 原生 |

### 11.5 复用的设计资产

以下**设计决策和经验**完全复用，不重新发明：

- 21 条 ADR 全部适用
- Pipeline 固定序 Handler 链模式
- 5 层得分模型 (rawValue → standardScore → rawScore → weightedScore → stageScore)
- 6 种变量解析体系
- 三级标准匹配 Fallback
- 奥运排名算法
- 三路径 DataPull 架构
- MQ 双通道隔离 (eval + ai-summary)
- Publisher Confirm 异步确认
- 幂等机制设计
- Stage 树装配算法
- 深拷贝隔离策略
- 事件不中断管道原则
- 先字段后逻辑的演进策略

---

## 附录 A: Maven 依赖清单

```xml
<!-- Spring Boot 3.3.x -->
<dependency>spring-boot-starter-web</dependency>
<dependency>spring-boot-starter-amqp</dependency>
<dependency>spring-boot-starter-data-redis</dependency>
<dependency>spring-boot-starter-validation</dependency>
<dependency>spring-boot-starter-actuator</dependency>

<!-- MyBatis-Plus -->
<dependency>mybatis-plus-boot-starter (3.5.x)</dependency>

<!-- MySQL -->
<dependency>mysql-connector-j</dependency>

<!-- JEXL -->
<dependency>commons-jexl3 (3.3)</dependency>

<!-- Hutool -->
<dependency>hutool-all (5.8.x)</dependency>

<!-- Caffeine Cache -->
<dependency>caffeine</dependency>

<!-- 文档 -->
<dependency>springdoc-openapi-starter-webmvc-ui</dependency>

<!-- 测试 -->
<dependency>spring-boot-starter-test</dependency>
<dependency>testcontainers (MySQL + RabbitMQ)</dependency>
```

## 附录 B: 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| Pipeline H3 重构范围大 | 回归风险 | 保留旧系统对照测试 + 增量单测 |
| DataPull 外部 API 依赖 | 取数失败 | 路径C 兜底 + 超时 + 重试 |
| MQ 消息丢失 | 评估中断 | Publisher Confirm + DLQ + 超时扫描器 |
| JEXL 表达式注入 | 安全风险 | 沙箱白名单 |
| 深拷贝大模型性能 | 创建方案慢 | 事务内批量 INSERT, stage<100 可控 |
| Java 17 环境兼容 | CI/CD 问题 | 先本地验证, Docker 标准化 |

---

> **下一步**: 按阶段 0 开始执行 — 搭建 Maven 多模块骨架 + 执行 DDL。
