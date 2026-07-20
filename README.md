# AI 评估系统 (eval-system)

> **AI 原生评估系统**: LLM 打分 + 规则引擎验证。AI 坐主桌，规则引擎当镜子。

## 架构哲学

```
LLM 通道 (默认)          规则引擎通道 (对比基线)
语义判断 | 上下文感知    vs    JEXL 确定计算 | 可审计路径
         └──────────┬──────────┘
              ┌─────▼─────┐
              │ 对比 + 仲裁 │  ← 差异分级 TRIVIAL/NOTABLE/SIGNIFICANT
              └─────┬─────┘
              ┌─────▼─────┐
              │  树聚合     │  ← 规则引擎负责 (永不交给 LLM)
              └─────┬─────┘
              ┌─────▼─────┐
              │ 事件 + 落库 │  ← 双通道 RED_LINE 检测
              └───────────┘
```

## Pipeline

```
H1 加载配置 → H2 提取指标 → H3 双通道打分+树聚合 → H4 事件/红线 → H6 汇总落库
```

## 快速启动

```bash
# 本地开发
bash restart.sh
curl http://localhost:8080/actuator/health

# Docker
docker-compose up -d
```

## API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/evaluation/execute` | POST | 执行评估 (双通道) |
| `/api/v1/evaluation/compare/stats` | GET | 双通道对比统计 |
| `/api/v1/evaluation/rank/{sceneCode}` | POST | 奥运排名 |
| `/api/v1/evaluation/summary/{id}` | POST/GET | AI 总结 (两轮自审) |
| `/api/v1/scene/copy` | POST | 从模型创建方案 |
| `/api/v1/scene/list` | GET | 方案列表 |
| `/api/v1/scene/{id}/publish` | POST | 发布方案 |
| `/api/v1/grade-mapping/list` | GET | 等级映射 CRUD |
| `/api/v1/grade-mapping/batch` | POST | 批量保存等级 |
| `/actuator/health` | GET | 健康检查 |
| `/swagger-ui.html` | GET | API 文档 (OpenAPI) |

## 技术栈

| 组件 | 版本 |
|------|------|
| JDK | 17 (Temurin) |
| Spring Boot | 3.3.5 |
| MyBatis-Plus | 3.5.7 |
| MySQL | 8.0 |
| JEXL | 3.3 |
| Caffeine | (via Spring Cache) |
| DeepSeek | API (LLM-as-Judge) |

## 开发进度

```
M0: ✅ 地基 (2/2)
M1: ✅ ★ AI 先打分 (8/8)
M2: ✅ 规则引擎+对比 (8/8)
M3: 🔄 完整系统 (9/12)
```

## 项目结构

```
eval-system/
├── eval-common/         # 枚举、异常、Result、ExpressionUtil
├── eval-domain/         # 25 Entity + Service 接口
├── eval-infrastructure/ # 25 Mapper + LLM + DataPull
├── eval-application/    # Pipeline + Handler + Strategy + DomainService
├── eval-api/            # Controller + Request/Response DTO
├── eval-boot/           # Spring Boot 入口 + 测试 + 配置
├── docs/sql/            # DDL 迁移脚本
├── Dockerfile
├── docker-compose.yml
└── restart.sh
```

## License

Apache 2.0
