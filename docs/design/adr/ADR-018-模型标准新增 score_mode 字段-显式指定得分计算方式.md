---
adr: ADR-018
title: "模型标准新增 score_mode 字段，显式指定得分计算方式"
status: Accepted
date: 2026-05-15
project: AI评估组件
---

## ADR-018：模型标准新增 score_mode 字段，显式指定得分计算方式

| 项目 | 内容 |
|---|---|
| **上下文** | `dr_model_index_standard` 表中 `score` 和 `weight` 字段的组合含义不明确。当前代码用字段值（0/非0）来隐式推断得分计算方式，导致：① `score=0, weight=80` 的标准被当作"无得分"跳过；② `score=100, weight=0` 被当作"固定分100"但实际可能是区间权重场景；③ 无法表达"固定分 × 权重"的组合。实际业务场景需要多种灵活的得分计算方式，用字段值猜测计算方式既不清晰也不可靠。 |
| **决策** | 在 `dr_model_index_standard` 表新增 `score_mode` 字段（VARCHAR(30), DEFAULT NULL），显式指定得分计算方式。四种模式：`RAW_WEIGHT`（原始值乘权重）、`FIXED`（固定分）、`FIXED_WEIGHT`（固定分乘权重）、`INTERVAL_WEIGHT`（区间权重，JSON 格式）。计算时先读 `score_mode`，再根据模式取对应字段值计算，不依赖字段值推断。 |
| **备选方案** | ① 复用现有 `type` 字段区分（`type` 语义偏"标准分类"，承载"得分计算方式"可能混淆）；② 用 `score`/`weight` 的值组合隐式推断（当前做法，已证明不可靠）；③ 用 `dimension_rule` JSON 格式自动推断（JSON 格式只区分区间/布尔，无法区分 RAW_WEIGHT/FIXED/FIXED_WEIGHT） |
| **影响** | 新增一个字段，需改表、改 Entity/Vo、改前端表单；旧数据 `score_mode=NULL` 时按兼容逻辑处理（基于 dimension_rule 格式 + score/weight 值推断）；新增标准时前端必须选择 score_mode，消除歧义 |

### ADR-018 附录：score_mode 四种模式详解

| score_mode | 计算公式 | 用到的字段 | 典型场景 |
|------------|---------|-----------|---------|
| `RAW_WEIGHT` | rawValue × weight | rawValue, weight | 入司天数≥90时，日志填报率 × 80% |
| `FIXED` | score | score | 条件满足给固定分（如及格/不及格） |
| `FIXED_WEIGHT` | score × weight | score, weight | 日志填报率 60%-80% 给 60 分 × 70% |
| `INTERVAL_WEIGHT` | rawValue × intervalWeight | rawValue, dimension_rule(JSON) | 入司天数决定权重区间，原始值乘区间权重 |

### ADR-018 附录：计算示例

以日志填报率（rawValue=95.5）为例，入司天数=1407（在 [90, +∞) 区间）：

| score_mode | score | weight | 计算过程 | 结果 |
|------------|-------|--------|---------|------|
| `RAW_WEIGHT` | 0 | 80 | 95.5 × 80 | 76.4 |
| `FIXED` | 100 | 0 | 100 | 100 |
| `FIXED_WEIGHT` | 85 | 80 | 85 × 80 | 68 |
| `INTERVAL_WEIGHT` | - | - | 95.5 × 0.8（区间权重） | 76.4 |

注意：上表计算的是 `RuleScoreStrategy.score()` 返回的 score 值。Handler3 层还会执行 `score × modelWeight`（dr_model_index.weight）得到最终加权得分。

---
