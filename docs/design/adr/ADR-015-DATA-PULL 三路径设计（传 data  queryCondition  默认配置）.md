---
adr: ADR-015
title: "DATA-PULL 三路径设计（传 data / queryCondition / 默认配置）"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-015：DATA-PULL 三路径设计（传 data / queryCondition / 默认配置）

| 项目 | 内容 |
|---|---|
| **上下文** | 评估数据的来源有多种场景——调用方可能已有数据、可能需要临时调整查询条件、也可能完全依赖默认配置。 |
| **决策** | Controller 层 DATA-PULL 支持三条路径：① 传了 `data` → 直接使用；② 未传 data 但传了 `queryCondition` → 覆盖默认条件后拉取；③ 都没传 → 用评估对象默认配置拉取。数据源格式自动路由（http/开头走接口，其他走 SQL）。 |
| **备选方案** | ① 只支持默认配置拉取，其他场景由调用方自行适配；② 每种路径拆成独立接口 |
| **影响** | 一个接口覆盖所有数据来源场景，灵活性高；代价是 Controller 层分支逻辑较多，需要充分测试各路径 |

---
