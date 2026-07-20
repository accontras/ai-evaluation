---
adr: ADR-019
title: "扩大 attrValues 而非绕过它，dimDefinitions 作为数据源无关的翻译层"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-019：扩大 attrValues 而非绕过它，dimDefinitions 作为数据源无关的翻译层

| 项目 | 内容 |
|---|---|
| **上下文** | 红线事件表达式引用的维度不在模型指标的 `dimensions` 列表中，`FetchIndicatorValuesHandler` 只提取指标关联维度到 `attrValues`，导致 `buildExpressionParams` 缺少事件所需维度值，表达式求值失败。修复时存在两条路径：① 在 `buildExpressionParams` 中直接加入 `rawData.fields` 绕过 `attrValues`；② 扩大 `attrValues` 的属性列表，让事件维度也通过 `dimDefinitions` 映射进入。 |
| **决策** | 采用方案②：在 `FetchIndicatorValuesHandler` 中新增 `supplementAttrValuesFromDimDefinitions` 方法，遍历所有 `dimDefinitions`，将尚未在 `attrValues` 中的维度通过 `fieldCode` 从 `rawData.fields` 取值、以 `dimName` 为 key 补充到 `attrValues`。`buildExpressionParams` 保持原有逻辑不变。 |
| **备选方案** | ① 在 `buildExpressionParams` 中直接加入 `rawData.fields`（绕过 attrValues 和 dimDefinitions 映射）；② 在 `buildExpressionParams` 中用 dimDefinitions 翻译 rawData.fields 的所有字段 |
| **影响** | `attrValues` 成为所有维度值的唯一权威数据源；`dimDefinitions` 作为规范翻译层的架构保持一致（与 ADR-007 一脉相承）；未来 API 输入场景只需确保 `attrValues` 被正确填充，表达式求值逻辑无需改动；改动范围最小（只改 FetchIndicatorValuesHandler 一处） |

### ADR-019 附录：方案否决理由

方案一（绕过 attrValues 直接加 rawData.fields）被否决的关键原因：

1. **架构退化**：`dimDefinitions` 翻译层被架空，数据流变成"两条路"——指标维度走 `attrValues` + 映射，事件维度走 `rawData.fields` 直接访问，违反 ADR-007 原则
2. **数据源耦合**：表达式直接依赖 `rawData.fields` 的 key 格式，CSV 用 fieldCode 但 API 可能用 dimName，失去抽象层
3. **命名空间污染**：`rawData.fields` 可能包含非维度字段，直接加入表达式参数导致命名冲突
4. **未来扩展风险**：API 输入场景上线后需再次修改 `buildExpressionParams`，而方案②只需确保 `attrValues` 被正确填充

---
