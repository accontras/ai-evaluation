# Changelog

## v1.0.0 (2026-07-21)

### AI 工程化
- **A1 Prompt 工程**: 3 版本管理 (v1-base/v2-standards/v3-fewshot), DB 存储, 运行时切换
- **A2 LLM 可观测性**: eval_ai_experiment 全链路追踪, token/延迟/P95/异常/成本
- **A3 RAG 检索**: SimilarCaseService 特征相似度 + few-shot 注入
- **A4 AI 可靠性**: ResilientLlmClient 熔断/重试/fallback 链 (deepseek→glm→qwen)

### 核心系统
- LLM-as-Judge 打分 (DeepSeek)
- 双通道对比 (LLM vs 规则引擎, TRIVIAL/NOTABLE/SIGNIFICANT)
- Stage 树聚合 (TOP/NORMAL/LEAF, 自底向上)
- 事件/红线双通道检测 (RULE/LLM/BOTH)
- 等级映射 S/A/B/C/D + 奥运排名
- AI 总结两轮自审
- 深拷贝 + 方案管理
- 申诉体系

### 基础设施
- LlmClient 接口化 (OpenAiCompatibleLlmClient)
- Caffeine 缓存 (TTL 5min)
- Dashboard (Chart.js)
- Docker + docker-compose
- 重启脚本 restart.sh

## v0.3.0-m3 (2026-07-20)
- 深拷贝 + 方案 API
- 等级映射 + 排名 + 回调
- AI 总结两轮对话
- Caffeine 缓存

## v0.2.0-m2 (2026-07-20)
- JEXL 表达式引擎
- 规则引擎评分策略
- 双通道对比引擎
- Stage 树装配 + 聚合
- TOP 路由
- H4 事件/红线

## v0.1.0-m1 (2026-07-20)
- 开发环境搭建
- 25 张表数据库
- Pipeline 骨架
- LLM 客户端
- LLM-as-Judge 打分
- 端到端验证
