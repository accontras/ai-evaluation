## 1. LlmProperties 重构

- [x] 1.1 新增 `ModelConfig` 内部类（provider/baseUrl/apiKey/model/temperature）
- [x] 1.2 `LlmProperties` 新增 primary (ModelConfig) + fallbacks (List<ModelConfig>) + retry/circuit/tokenBudget 配置项
- [x] 1.3 保留旧字段兼容（@Deprecated），映射到 primary

## 2. LlmConfig 重构

- [x] 2.1 `resilientLlmClient` Bean 从 `LlmProperties` 的 primary/fallbacks 数组创建客户端
- [x] 2.2 每个 fallback 使用独立的 baseUrl/apiKey/model（不再复用 primary 配置）
- [x] 2.3 application.yml 添加 GLM fallback 配置（glm-4-flash + 真实 key）

## 3. ResilientLlmClient 增强

- [x] 3.1 半开状态原子变量 + 真探测逻辑（halfOpen + probeInProgress）
- [x] 3.2 Token 预算保护：`checkTokenBudget()` 方法
- [x] 3.3 `getLastDegradationLevel()` 方法 + `degradationLevel` 追踪
- [x] 3.4 `getStatus()` 增强：返回 models 统计 + degradation 分布 + tokenBudget

## 4. DB + Entity 更新

- [x] 4.1 `ALTER TABLE eval_ai_experiment ADD COLUMN degradation_level`
- [x] 4.2 `EvalAiExperiment` Entity 新增 `degradationLevel` 字段 + getter/setter

## 5. LlmScoringStrategy 适配

- [x] 5.1 `recordExperiment()` 写入 `degradationLevel`
- [x] 5.2 降级 L2/L3 时返回 DEGRADED 标记

## 6. 可视化 API

- [x] 6.1 创建 `ResilienceController`：`GET /api/v1/ai/resilience-status`
- [x] 6.2 返回 circuit/models/degradation/tokenBudget JSON

## 7. 测试验证

- [x] 7.1 单元测试：`RagQualityServiceTest` 风格，测熔断计数 + 半开逻辑
- [x] 7.2 集成测试：配置真实 GLM key → 跑一次评估 → 检查 degradation_level
- [x] 7.3 手动验证：resilience-status API 返回正确数据
