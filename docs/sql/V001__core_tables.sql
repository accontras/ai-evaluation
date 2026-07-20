-- ============================================
-- AI 评估系统 — 核心表 DDL (S2: 5张)
-- 数据库: eval_db
-- ============================================

-- 1. 评估模型主表
CREATE TABLE IF NOT EXISTS eval_model (
    id              VARCHAR(20)   PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(100),
    status          VARCHAR(20)   DEFAULT 'ENABLED',
    aggregate_mode  VARCHAR(20)   DEFAULT 'weighted_sum' COMMENT '聚合模式: weighted_sum/sum/min/score_accumulate',
    dimensions      VARCHAR(500)  COMMENT '维度定义 JSON',
    dimension_options VARCHAR(500),
    vn              INT           DEFAULT 1 COMMENT '乐观锁版本号',
    memo            VARCHAR(500),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估模型主表';

-- 2. 模型维度树
CREATE TABLE IF NOT EXISTS eval_model_stage (
    id              VARCHAR(20)   PRIMARY KEY,
    model_id        VARCHAR(20)   NOT NULL,
    parent_id       VARCHAR(20)   COMMENT '父 stage (树结构)',
    type            VARCHAR(10)   DEFAULT 'normal' COMMENT 'top / normal / leaf',
    level           INT           DEFAULT 0 COMMENT '层级深度',
    code            VARCHAR(50),
    name            VARCHAR(100),
    sn              INT           COMMENT '排序',
    weight          INT           COMMENT '权重',
    priority        INT           DEFAULT 0,
    aggregate_mode  VARCHAR(20)   COMMENT '聚合模式',
    default_score   DECIMAL(10,2) COMMENT '叶子Stage兜底得分',
    memo            VARCHAR(500),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_model_parent (model_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型维度树';

-- 3. 模型-指标关联
CREATE TABLE IF NOT EXISTS eval_model_index (
    id              VARCHAR(20)   PRIMARY KEY,
    model_id        VARCHAR(20)   NOT NULL,
    stage_id        VARCHAR(20)   NOT NULL COMMENT '关联 stage',
    index_id        VARCHAR(20)   NOT NULL COMMENT '关联 eval_index',
    sn              INT           COMMENT '排序',
    score_cap       DECIMAL(10,2) COMMENT '得分上限',
    score_floor     DECIMAL(10,2) COMMENT '得分下限',
    query_data_set  VARCHAR(100)  COMMENT '数据组件 viewCode',
    dimension_options TEXT        COMMENT '维度映射 JSON',
    data_source     VARCHAR(100)  COMMENT '数据源配置',
    memo            VARCHAR(500),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_model_stage (model_id, stage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型-指标关联表';

-- 4. 指标定义表
CREATE TABLE IF NOT EXISTS eval_index (
    id              VARCHAR(20)   PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(100),
    catalog         VARCHAR(50)   COMMENT '指标分类',
    unit            VARCHAR(20)   COMMENT '单位',
    calculate_type  VARCHAR(100)  DEFAULT 'BASIC' COMMENT 'BASIC / DERIVED',
    calculate_rule  TEXT          COMMENT 'JEXL 表达式 (派生指标)',
    relate_index    VARCHAR(500)  COMMENT '依赖指标编码',
    layer           INT           DEFAULT 0 COMMENT '拓扑层级',
    dimensions      VARCHAR(500)  COMMENT '维度 JSON',
    index_field_code VARCHAR(255) COMMENT '指标字段编码',
    query_data_set  TEXT          COMMENT 'data-view 定义',
    dimension_options TEXT        COMMENT '维度配置',
    filter_options  TEXT          COMMENT '查询条件',
    status          VARCHAR(20)   DEFAULT 'ENABLED',
    memo            VARCHAR(500),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标定义表';

-- 5. 评估方案表
CREATE TABLE IF NOT EXISTS eval_scene (
    id              VARCHAR(20)   PRIMARY KEY,
    code            VARCHAR(60)   NOT NULL,
    model_id        VARCHAR(20)   NOT NULL,
    name            VARCHAR(100),
    target_code     VARCHAR(100)  COMMENT '评估对象 code',
    aggregate_mode  VARCHAR(20)   DEFAULT 'weighted_sum',
    status          VARCHAR(20)   DEFAULT 'DRAFT',
    evaluate_mode   VARCHAR(10)   COMMENT '评估模式',
    callback_api    VARCHAR(120),
    callback_token  VARCHAR(128),
    options         TEXT          COMMENT '扩展配置 JSON',
    vn              INT           DEFAULT 1 COMMENT '乐观锁版本号',
    memo            VARCHAR(500),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估方案表';
