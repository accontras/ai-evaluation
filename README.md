# AI 评估系统 (eval-system)

> **AI 原生评估系统**: LLM 打分 + 规则引擎验证。AI 坐主桌，规则引擎当镜子。

[![Java](https://img.shields.io/badge/Java-17-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)
[![DeepSeek](https://img.shields.io/badge/LLM-DeepSeek-blueviolet)](https://deepseek.com)

## 核心理念

```
LLM 通道 (默认)          规则引擎通道 (对比基线)
语义判断 | 上下文感知    vs    JEXL 确定计算 | 可审计路径
         └──────────┬──────────┘
              ┌─────▼─────┐
              │ 对比 + 仲裁 │  ← TRIVIAL / NOTABLE / SIGNIFICANT
              └─────┬─────┘
              ┌─────▼─────┐
              │  Stage 树  │  ← 规则引擎负责 (审计底线)
              └─────┬─────┘
              ┌─────▼─────┐
              │  落库 + 展示│
              └───────────┘
```

**三个 AI 切入点**: H3 打分 / H4 异常检测 / H6 总结 —— 恰好是评估系统里最有判断力的三个环节。

## 快速启动

```bash
# 1. 准备 MySQL 数据库
mysql -u root -e "CREATE DATABASE eval_db"
mysql -u root eval_db < docs/sql/V003__clean_schema.sql
mysql -u root eval_db < docs/sql/V004__seed_data.sql

# 2. 配置 API Key
# 方式A: 环境变量 (CI/他人)
export DEEPSEEK_API_KEY=sk-your-key
export GLM_API_KEY=your-glm-key
export DB_PASSWORD=your-db-password

# 方式B: 本地开发 (创建 application-default.yml, 已 gitignore)
# 复制密钥到 eval-boot/src/main/resources/application-default.yml

# 3. 构建 + 启动
bash restart.sh

# 4. 打开 Dashboard
open http://localhost:8080/
```

## 功能全景

| 模块 | 能力 | 说明 |
|------|------|------|
| 🤖 **AI 打分** | LLM-as-Judge | DeepSeek 语义理解, 有据可查 |
| ⚖️ **双通道对比** | LLM vs 规则引擎 | TRIVIAL/NOTABLE/SIGNIFICANT 差异分级 |
| 🌳 **Stage 树** | TOP/NORMAL/LEAF | 自底向上加权聚合, 路由分叉 |
| 🚨 **事件/红线** | 双通道检测 | RULE/LLM/BOTH 交叉验证, 红线×0.6 |
| 📊 **等级排名** | S/A/B/C/D + 奥运排名 | 同分并列 1,1,3,4 |
| 📝 **AI 总结** | 两轮自审 | Round1 生成 → Round2 审阅修正 |
| 🔍 **RAG 检索** | 特征相似度 | 历史案例检索 + few-shot 注入 |
| 🛡️ **AI 可靠性** | 熔断/重试/fallback | deepseek→glm→qwen 链 |
| 📈 **可观测性** | 全链路追踪 | token/延迟/P95/异常/成本 |
| 🎨 **Dashboard** | Chart.js 可视化 | 一键触发 + 实时结果 + 图表对比 |

## API 速查

| 端点 | 方法 | 说明 |
|------|------|------|
| `POST /api/v1/evaluation/execute` | POST | 执行评估 |
| `GET /api/v1/evaluation/compare/stats` | GET | 对比统计 |
| `POST /api/v1/evaluation/rank/{scene}` | POST | 奥运排名 |
| `POST /api/v1/evaluation/summary/{id}` | POST | AI 总结 |
| `GET /api/v1/evaluation/experiments/stats` | GET | 实验统计 |
| `GET /api/v1/evaluation/resilience` | GET | 韧性状态 |
| `GET /api/v1/evaluation/similar-cases/{code}/{val}` | GET | 相似案例(RAG) |
| `GET /api/v1/prompts` | GET | Prompt 版本管理 |
| `POST /api/v1/scene/copy` | POST | 创建方案 |

## 技术栈

Java 17 · Spring Boot 3.3 · MyBatis-Plus 3.5 · MySQL 8 · JEXL 3.3 · Caffeine · DeepSeek API · Chart.js

## 文档

📖 **[系统全书](docs/BOOK.md)** — 16 章完整手册: 架构/概念/ADR/实现/AI/API

| 文档 | 说明 |
|------|------|
| [BOOK.md](docs/BOOK.md) | 系统全书 |
| [AI-IMPLEMENTATION.md](docs/AI-IMPLEMENTATION.md) | AI 实现详解 |
| [LLM-SCORING-DESIGN.md](docs/design/LLM-SCORING-DESIGN.md) | LLM 打分设计 |
| [A1.2-PROMPT-VERSIONING.md](docs/design/A1.2-PROMPT-VERSIONING.md) | Prompt 版本化 |
| [A2-LLM-OBSERVABILITY.md](docs/design/A2-LLM-OBSERVABILITY.md) | 可观测性设计 |
| [adr/](docs/design/adr/) | 21 条架构决策 |
| [DEVELOPMENT-PLAN.md](DEVELOPMENT-PLAN.md) | 开发计划 |
| [AGENTS.md](AGENTS.md) | 编码规范 |

## 版本

| Tag | 里程碑 |
|-----|--------|
| `v0.1.0-m1` | AI 先打分 |
| `v0.2.0-m2` | 双通道对比 |
| `v0.3.0-m3` | 完整系统 |
| `v1.0.0` | 正式发布 |

## License

Apache 2.0
