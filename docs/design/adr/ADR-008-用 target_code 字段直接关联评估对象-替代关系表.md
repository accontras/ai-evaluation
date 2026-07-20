---
adr: ADR-008
title: "用 target_code 字段直接关联评估对象，替代关系表"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-008：用 target_code 字段直接关联评估对象，替代关系表

| 项目 | 内容 |
|---|---|
| **上下文** | V1.2 设计中评估场景与评估对象的关联通过 `dr_scene_target_rel` 关系表实现，但实际业务上一个评估场景只关联一个评估对象，是 N:1 而非 N:M 关系。 |
| **决策** | 删除 `dr_scene_target_rel` 关系表，在 `dr_model_scene` 上用 `target_code` 字段直接关联评估对象。 |
| **备选方案** | ① 保留关系表支持多对多；② 用 JSON 数组字段存储多个 target_code |
| **影响** | 表结构简化，查询无需 JOIN 关系表；如果未来需要"一个场景评估多种对象类型"，需要重新引入关联机制 |

---
