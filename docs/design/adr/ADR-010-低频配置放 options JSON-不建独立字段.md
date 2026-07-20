---
adr: ADR-010
title: "低频配置放 options JSON，不建独立字段"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-010：低频配置放 options JSON，不建独立字段

| 项目 | 内容 |
|---|---|
| **上下文** | 排序配置（rankingEnabled、rankingOrder）属于低频配置，只有部分评估场景需要。如果为每个低频配置都加独立字段，表结构会不断膨胀。 |
| **决策** | 排序配置统一放入 `dr_model_scene.options` JSON 字段中。 |
| **备选方案** | ① 加 `ranking_enabled` 和 `ranking_order` 独立字段；② 新建排序配置子表 |
| **影响** | 表结构稳定，新增低频配置只需扩展 JSON 结构；代价是查询和校验需要解析 JSON，无法直接 SQL 筛选 |

---
