# AGENTS.md — AI 评估系统编码规范

> 本文档为 AI 编码助手（Claude Code 等）和开发者提供项目级的编码约定。
> 所有代码生成、重构、Review 必须遵守本文档。

---

## 一、分层架构

```
Controller        ← 参数校验 + 调用 DomainService / Service
  ↓
DomainService     ← 复杂业务编排: Pipeline 执行、深拷贝、申诉重算
  ↓
Service           ← 单表 CRUD: 不包含 if/else 业务判断
  ↓
Mapper            ← 数据访问: MyBatis-Plus BaseMapper
```

### 各层职责

| 层 | 能做什么 | 不能做什么 |
|----|---------|-----------|
| **Controller** | 参数校验(`@Valid`)、调 DomainService/Service、组装 Result | 不写业务逻辑、不直接调 Mapper |
| **DomainService** | 跨表事务、Pipeline 编排、深拷贝、申诉重算 | 不写单表 CRUD |
| **Service** | 单表增删改查分页 | **不写 if/else 业务判断** |
| **Mapper** | SQL 数据访问 | 不写业务逻辑 |

### 调用规则

- Controller → DomainService ✅
- Controller → Service ✅ (简单 CRUD)
- Controller → Mapper ❌
- DomainService → Service ✅
- DomainService → Mapper ✅ (Pipeline Handler 里可直接调)
- Service → Mapper ✅
- Service → DomainService ❌
- Service → Service ❌ (不同 Service 不要互相调)

### DomainService 清单

| DomainService | 职责 |
|---------------|------|
| `EvaluationDomainService` | 评估执行: DataPull → Pipeline → Ranking → Callback |
| `SceneConfigDomainService` | 方案创建: 深拷贝(5步) + 发布 |
| `AppealDomainService` | 申诉: 提交/批量导入/执行重算 |
| `ModelConfigDomainService` | 模型配置: 级联保存(模型+Stage树+指标+事件+标准) |

### Service 命名规范

```
EvalModelService     ← 单表 CRUD (eval_model)
EvalSceneService     ← 单表 CRUD (eval_scene)
EvalIndexService     ← 单表 CRUD (eval_index)
...
```

## 二、包结构

```
io.github.accontra.eval
├── api/
│   ├── controller/    ← REST Controller
│   ├── request/       ← 请求 DTO (Record)
│   └── response/      ← 响应 DTO (Record)
├── application/
│   ├── pipeline/      ← ConfigurablePipeline
│   ├── handler/       ← H1~H6 Handler
│   ├── strategy/      ← 评分策略 (RuleScoreStrategy, LlmScoringStrategy)
│   └── service/       ← DomainService 实现
├── domain/
│   ├── model/         ← Entity (25张表)
│   └── service/       ← Service 接口 + DomainService 接口
├── infrastructure/
│   ├── mapper/        ← MyBatis-Plus Mapper
│   ├── datapull/      ← DataPullService
│   ├── mq/            ← RabbitMQ Producer/Consumer
│   └── cache/         ← Caffeine Cache
├── common/
│   ├── enums/         ← StageType, EventType, AggregateMode, ScoreMode, AppealType
│   ├── constant/      ← ErrorCode
│   ├── exception/     ← EvalException
│   └── Result.java    ← 统一响应
└── EvalApplication.java
```

## 三、技术栈与约定

### 数据库

- 包名: `io.github.accontra.eval`
- 表前缀: `eval_`
- 主键: `BIGINT AUTO_INCREMENT`
- 逻辑删除: `enabled TINYINT DEFAULT 1` (@TableLogic)
- 时间戳: `create_time` / `update_time` (DATETIME)
- 业务状态: `status VARCHAR` (仅工作流表)

### Java 17 规范

- VO/DTO 用 Record: `public record CreateModelRequest(...) {}`
- 实体类用普通 POJO + getter/setter (MyBatis-Plus 需要)
- 禁止: `var`、`List.of`、`Map.of` — 这些是 Java 9+ 没问题，但保持一致

### 验证标准

每个 Session 结束前:
1. `mvn compile` — 零错误
2. `mvn test` — 新增测试全绿
3. **前端验证** — 浏览器打开 `http://localhost:8080/`，能用页面操作新功能（不依赖 curl/Postman）
4. `git commit` — 一个 session 一个 commit

### 前后端配套

**每个后端 API 必须同步开发前端页面。** 前端位置: `eval-boot/src/main/resources/static/index.html`。
不可交付"只能用 curl 调"的功能——Dashboard 上点按钮操作才是真正的验收。

## 四、Git 提交规范

```
S{N}: {简短描述}

例:
S1: 项目骨架 + JDK 17
S2: 25张表 clean schema (V003)
S3: eval-common 枚举
S4: 25 Entity + 25 Mapper
S5: Pipeline 骨架 + Handler 接口
```

## 五、环境

| 项 | 值 |
|----|-----|
| JDK 17 | `~/.jdks/temurin-17` (Temurin 17.0.19) |
| 切换脚本 | `source setup-env.sh` |
| MySQL | `localhost:3306` / `eval_db` / root |
| 包名 | `io.github.accontra.eval` |
