---
adr: ADR-007
title: "用 dr_data_dimension.field_code 替代属性子表做维度映射"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-007：用 dr_data_dimension.field_code 替代属性子表做维度映射

| 项目 | 内容 |
|---|---|
| **上下文** | V1.2 设计中评估对象属性通过 `dr_evaluation_target_item` 子表存储，需要手工配置每个属性的 field_code 映射，维护成本高且与维度定义重复。 |
| **决策** | 删除 `dr_evaluation_target_item` 子表，统一使用 `dr_data_dimension.field_code` 作为映射桥梁。评估对象的 `dimensions` 字段声明使用的维度，`dr_data_dimension` 提供 `name → field_code` 映射，从 rawData 中实时提取属性值。 |
| **备选方案** | ① 保留属性子表，手工配置映射；② 在评估对象表上直接加 JSON 字段存映射关系 |
| **影响** | 维度定义只维护一处（dr_data_dimension），评估对象只需声明维度名称即可自动映射；属性值从实际业务数据中实时提取，无需手工配置静态属性 |

---
