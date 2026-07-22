# Brainstorm Summary

- Change: llm-resilience-upgrade
- Date: 2026-07-22

## 确认的技术方案

- LlmProperties: ModelConfig 内部类 + primary/fallbacks 数组配置
- 半开: AtomicBoolean halfOpen + probeInProgress CAS
- Token 预算: executeWithResilience 入口检查
- degradation_level: ThreadLocal → recordExperiment 写入 DB
- API: ResilienceController 注入 ResilientLlmClient
- 模型: deepseek-chat(primary) + glm-4.5-air(fallback, key 用环境变量)

## 关键取舍与风险

- GLM key 用 ${GLM_API_KEY} 环境变量，不硬编码
- 只配 2 个模型（deepseek + glm），不配 qwen（用户用不起）
- 半开探测 1 次机会

## 测试策略

- 单元测试: 熔断计数 + 半开状态机
- 集成测试: 真实 GLM key 验证 L1 fallback

## Spec Patch

无
