-- ============================================
-- AI 评估系统 — 完整建库 DDL (25 张表)
-- 基于: poc-create-20260710.sql + 规则引擎4表
-- 变更: dr_前缀 → eval_, 去系统字段, id改BIGINT
-- 数据库: eval_db
-- ============================================

-- ========================================
-- 1. 模型定义层 (6 张表)
-- ========================================

CREATE TABLE eval_model (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(100),
    domain_code     VARCHAR(100)  COMMENT '业务领域',
    catagory_code   VARCHAR(60)   COMMENT '业务域',
    status          VARCHAR(8)    DEFAULT 'ENABLED',
    aggregate_mode  VARCHAR(20)   DEFAULT 'weighted_sum' COMMENT '聚合模式',
    dimensions      VARCHAR(500)  COMMENT '维度定义 JSON',
    dimension_options VARCHAR(500),
    vn              INT           DEFAULT 1 COMMENT '版本号',
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估模型';

CREATE TABLE eval_model_stage (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    model_id        BIGINT        NOT NULL,
    parent_id       BIGINT        COMMENT '父 stage (树)',
    type            VARCHAR(10)   DEFAULT 'normal' COMMENT 'top / normal / leaf',
    level           INT           DEFAULT 0,
    code            VARCHAR(50),
    name            VARCHAR(100),
    sn              INT           COMMENT '排序',
    weight          INT           COMMENT '权重',
    priority        INT           DEFAULT 0,
    aggregate_mode  VARCHAR(20)   COMMENT '聚合模式',
    default_score   DECIMAL(10,2) COMMENT '叶子stage兜底得分',
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_model_parent (model_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型维度树';

CREATE TABLE eval_model_index (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    model_id        BIGINT        NOT NULL,
    stage_id        BIGINT        NOT NULL COMMENT '关联 stage',
    index_id        BIGINT        NOT NULL COMMENT '关联 eval_index',
    sn              INT           COMMENT '排序',
    score_cap       DECIMAL(10,2) COMMENT '得分上限',
    score_floor     DECIMAL(10,2) COMMENT '得分下限',
    query_data_set  VARCHAR(100)  COMMENT 'viewCode',
    dimension_options TEXT        COMMENT '维度映射 JSON',
    data_source     VARCHAR(100)  COMMENT '数据源',
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_model_stage (model_id, stage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型指标关联';

CREATE TABLE eval_model_event (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    model_id        BIGINT,
    scene_id        BIGINT        COMMENT 'NULL=模板层, 非NULL=方案层',
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    event_type      VARCHAR(20)   NOT NULL COMMENT 'RED_LINE/MARK/BONUS/DEDUCT',
    dimension_rule  TEXT          COMMENT 'JEXL 触发条件',
    score_expression VARCHAR(200) COMMENT '分值表达式',
    priority        INT           DEFAULT 0,
    red_line_message VARCHAR(500),
    risk_level      VARCHAR(20),
    target_type     VARCHAR(10)   DEFAULT 'MODEL' COMMENT 'MODEL/STAGE/INDEX',
    target_id       BIGINT        COMMENT '挂载实体ID',
    rule_id         BIGINT        COMMENT '关联 eval_decision_rule',
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_code (model_id, code),
    INDEX idx_model (model_id),
    INDEX idx_scene (scene_id),
    INDEX idx_target (model_id, target_type, target_id),
    INDEX idx_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估事件配置';

CREATE TABLE eval_model_standard (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    model_id        BIGINT,
    stage_id        BIGINT,
    index_id        BIGINT,
    model_index_id  BIGINT,
    scene_id        BIGINT        COMMENT 'NULL=模板层',
    target_type     VARCHAR(10)   DEFAULT 'STAGE',
    target_id       BIGINT,
    code            VARCHAR(50),
    standard_type   VARCHAR(20)   DEFAULT 'STRUCTURED' COMMENT 'STRUCTURED/EXPRESSION',
    dimension_rule  TEXT          COMMENT 'JEXL 条件表达式',
    min_value       DECIMAL(10,2),
    max_value       DECIMAL(10,2),
    dict_value      VARCHAR(50),
    score           DECIMAL(10,2) COMMENT '命中得分',
    score_mode      VARCHAR(30)   COMMENT 'RAW_WEIGHT/FIXED/INTERVAL_WEIGHT/FIXED_WEIGHT',
    score_type      VARCHAR(20)   DEFAULT 'FIXED' COMMENT 'FIXED/FORMULA',
    score_expression TEXT         COMMENT 'JEXL 得分公式',
    priority        INT,
    rule_id         BIGINT        COMMENT '关联 eval_decision_rule',
    enabled         TINYINT       DEFAULT 1,
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scene (scene_id),
    INDEX idx_target (target_type, target_id),
    INDEX idx_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型参考标准';

-- ========================================
-- 2. 方案层 (4 张表)
-- ========================================

CREATE TABLE eval_scene (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(60)   NOT NULL,
    model_id        BIGINT        NOT NULL,
    name            VARCHAR(160),
    target_code     VARCHAR(100)  COMMENT '评估对象 code',
    status          VARCHAR(18)   DEFAULT 'DRAFT',
    aggregate_mode  VARCHAR(20),
    evaluate_mode   VARCHAR(10)   COMMENT '0加权/1事件积分/2混合',
    callback_api    VARCHAR(120),
    callback_token  VARCHAR(128),
    callback_body_template TEXT,
    callback_params VARCHAR(500),
    appeal_window_days INT,
    grade_mapping_mode VARCHAR(20) DEFAULT 'SCORE_RANGE',
    grade_config    TEXT          COMMENT '等级映射配置 JSON',
    default_route_branch VARCHAR(20) COMMENT '路由默认分支',
    red_line_type   INT           DEFAULT 0,
    rank_range      VARCHAR(20)   DEFAULT 'all',
    rank_type       VARCHAR(10)   DEFAULT 'ASC',
    dimension_field VARCHAR(500),
    default_event_score INT DEFAULT 0,
    options         TEXT          COMMENT '扩展配置 JSON',
    vn              INT           DEFAULT 1,
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估方案';

CREATE TABLE eval_scene_stage (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    scene_id        BIGINT        NOT NULL,
    source_id       BIGINT        COMMENT '模板层原ID',
    parent_id       BIGINT        COMMENT '父 stage',
    type            VARCHAR(10)   DEFAULT 'normal' COMMENT 'top/normal/leaf',
    code            VARCHAR(50),
    name            VARCHAR(100),
    weight          INT,
    priority        INT,
    sn              INT,
    level           INT           DEFAULT 0,
    aggregate_mode  VARCHAR(20)   DEFAULT 'weighted_avg',
    default_score   DECIMAL(10,2),
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scene_parent (scene_id, parent_id),
    INDEX idx_source (source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方案维度树(深拷贝)';

CREATE TABLE eval_scene_index (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    scene_id        BIGINT,
    model_code      VARCHAR(50),
    stage_id        BIGINT,
    stage_code      VARCHAR(50),
    index_code      VARCHAR(50),
    index_name      VARCHAR(50),
    clazz           VARCHAR(50),
    weight          INT           COMMENT '权重(废弃)',
    priority        INT,
    source_id       BIGINT        COMMENT '模板层原ID',
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scene_stage (scene_id, stage_id),
    INDEX idx_source (source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方案指标映射';

-- ========================================
-- 3. 指标定义 (3 张表)
-- ========================================

CREATE TABLE eval_index (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(100),
    catalog         VARCHAR(50)   COMMENT '分类',
    unit            VARCHAR(20),
    index_field_code VARCHAR(255) COMMENT 'API返回字段名',
    calculate_type  VARCHAR(100)  DEFAULT 'BASIC' COMMENT 'BASIC/DERIVED',
    calculate_rule  TEXT          COMMENT 'JEXL 表达式(派生指标)',
    relate_index    VARCHAR(500)  COMMENT '依赖指标编码',
    layer           INT           DEFAULT 0 COMMENT '拓扑层级',
    dimensions      VARCHAR(500)  COMMENT '维度 JSON',
    query_data_set  TEXT          COMMENT 'data-view 定义',
    dimension_options TEXT        COMMENT '维度配置',
    filter_options  TEXT          COMMENT '查询条件',
    status          VARCHAR(8)    DEFAULT 'ENABLED',
    vn              INT           DEFAULT 1,
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标定义';

CREATE TABLE eval_index_catalog (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(50),
    name            VARCHAR(100),
    parent_id       BIGINT,
    level_no        VARCHAR(100),
    sn              INT,
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标分类';

CREATE TABLE eval_index_standard (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    model_id        BIGINT,
    stage_id        BIGINT,
    index_id        BIGINT,
    model_index_id  BIGINT,
    code            VARCHAR(50),
    name            VARCHAR(100),
    dimension_rule  TEXT          COMMENT 'JEXL 条件',
    min_value       DECIMAL(10,2),
    max_value       DECIMAL(10,2),
    dict_value      VARCHAR(50),
    score           DECIMAL(10,2),
    score_mode      VARCHAR(30)   DEFAULT 'RAW_WEIGHT',
    priority        INT,
    rule_id         BIGINT        COMMENT '关联 eval_decision_rule',
    enabled         TINYINT       DEFAULT 1,
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用参考标准';

-- ========================================
-- 4. 评估对象 + 维度 (2 张表)
-- ========================================

CREATE TABLE eval_target (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(100),
    name            VARCHAR(100)  NOT NULL,
    target_type     VARCHAR(20)   COMMENT 'PERSON/ORG/PROJECT',
    status          VARCHAR(8),
    catagory_code   VARCHAR(60)   COMMENT '业务域',
    data_source     VARCHAR(50)   COMMENT '数据源',
    data_set        VARCHAR(50)   COMMENT '数据集',
    filter_condition VARCHAR(255) COMMENT '过滤条件 JSON',
    dimensions      VARCHAR(500)  COMMENT '维度 JSON',
    dimension_options VARCHAR(1000) COMMENT '维度映射 JSON',
    biz_id_field    VARCHAR(100)  COMMENT '唯一标识字段名',
    parent_id       BIGINT,
    level_no        VARCHAR(100),
    sn              INT,
    vn              INT           DEFAULT 1,
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code),
    INDEX idx_catagory (catagory_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估对象';

-- ========================================
-- 5. 规则引擎 (4 张表)
-- ========================================

CREATE TABLE eval_decision_rule (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    open_code       VARCHAR(100)  COMMENT '业务编码',
    code            VARCHAR(50),
    name            VARCHAR(255),
    catagory_code   VARCHAR(100)  COMMENT '业务领域',
    dimension_code  VARCHAR(255)  COMMENT '业务参数',
    type            VARCHAR(36),
    vn              INT           DEFAULT 1,
    status          VARCHAR(36),
    enabled         TINYINT       DEFAULT 1,
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_open_code (open_code),
    INDEX idx_code (code),
    INDEX idx_catagory (catagory_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分支规则';

CREATE TABLE eval_decision_scene (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    rule_id         BIGINT        COMMENT '规则ID',
    code            VARCHAR(50),
    name            VARCHAR(255),
    parent_id       BIGINT        COMMENT '父节点',
    level_no        VARCHAR(255),
    sn              INT,
    dimension_code  VARCHAR(50),
    operator        VARCHAR(20),
    data_value      VARCHAR(60),
    dimension_rule  VARCHAR(500),
    ref_rule_id     BIGINT,
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_code (code),
    INDEX idx_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分支场景(条件树)';

CREATE TABLE eval_dimension (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(255),
    type            VARCHAR(36)   COMMENT '字典引用自定义',
    field_code      VARCHAR(255)  COMMENT '数据字段(映射桥梁)',
    data_type       VARCHAR(36),
    data_value      VARCHAR(500),
    status          VARCHAR(36)   DEFAULT '10',
    parent_id       BIGINT,
    level_no        VARCHAR(255),
    sn              INT,
    options         TEXT,
    memo            VARCHAR(500),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code),
    INDEX idx_field_code (field_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务维度';

CREATE TABLE eval_simple_rule (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    open_code       VARCHAR(80)   COMMENT '业务编码',
    code            VARCHAR(50),
    name            VARCHAR(255),
    catagory_code   VARCHAR(100),
    dimension_code  VARCHAR(255),
    type            VARCHAR(36),
    dimension_rule  VARCHAR(1500) COMMENT '业务规则',
    ref_rule_id     BIGINT,
    vn              INT           DEFAULT 1,
    status          VARCHAR(36),
    memo            VARCHAR(500),
    enabled         TINYINT       DEFAULT 1,
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_code (code),
    INDEX idx_catagory (catagory_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简单规则';

-- ========================================
-- 6. 运行时日志 (6 张表)
-- ========================================

CREATE TABLE eval_task_log (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(50)   NOT NULL,
    scene_code      VARCHAR(64)   COMMENT '评估场景编码',
    model_code      VARCHAR(20),
    catagory_code   VARCHAR(60),
    eval_period     VARCHAR(20)   COMMENT '数据周期',
    biz_type        VARCHAR(50)   COMMENT '业务单据类型',
    status          VARCHAR(20)   COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    biz_status      VARCHAR(16)   DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLIC/ARCHIVED',
    vn              INT,
    start_time      DATETIME,
    end_time        DATETIME,
    trace_no        VARCHAR(50)   COMMENT '流水号',
    query_condition TEXT          COMMENT '查询条件 JSON',
    previous_task_log_id BIGINT   COMMENT '前一批次',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code),
    INDEX idx_scene_period_status (scene_code, eval_period, status),
    INDEX idx_scene_period_biz (scene_code, eval_period, biz_status),
    INDEX idx_catagory (catagory_code),
    INDEX idx_model (model_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估任务日志';

CREATE TABLE eval_object_log (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    task_log_id     BIGINT        NOT NULL COMMENT '关联 eval_task_log',
    scene_code      VARCHAR(64),
    model_code      VARCHAR(20),
    catagory_code   VARCHAR(60),
    target_code     VARCHAR(50)   COMMENT '评估对象 code',
    total_score     DECIMAL(10,2),
    risk_level      VARCHAR(20),
    grade           VARCHAR(4)    COMMENT 'S/A/B/C/D/E',
    grade_mapping_mode VARCHAR(20),
    appeal_adjusted_score DECIMAL(10,2),
    evidence_chain  TEXT          COMMENT '证据链 JSON',
    summary         VARCHAR(500)  COMMENT 'AI 评估总结',
    summary_status  VARCHAR(20)   DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
    eval_rank       INT           COMMENT '奥运排名',
    rank_total      INT           COMMENT '参与排序总数',
    worker_id       VARCHAR(255)  COMMENT '执行节点',
    eval_period     VARCHAR(100)  COMMENT '评价周期',
    appeal_re_evaluation_source VARCHAR(64),
    status          VARCHAR(20),
    memo            VARCHAR(255),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_log (task_log_id),
    INDEX idx_scene (scene_code),
    INDEX idx_scene_grade (scene_code, grade),
    INDEX idx_task_status (task_log_id, status),
    INDEX idx_task_rank (task_log_id, eval_rank),
    INDEX idx_status (status),
    INDEX idx_appeal_source (appeal_re_evaluation_source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估对象日志';

CREATE TABLE eval_object_log_msg (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    object_log_id   BIGINT        COMMENT '关联 eval_object_log',
    header          VARCHAR(600),
    param_in        TEXT          COMMENT '入参（大字段分离）',
    param_out       TEXT          COMMENT '出参（大字段分离）',
    error_msg       TEXT          COMMENT '错误消息（大字段分离）',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估对象日志消息（大字段分离）';

CREATE TABLE eval_indicator_log (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    object_log_id   BIGINT        NOT NULL COMMENT '关联 eval_object_log',
    task_log_id     BIGINT,
    clazz           VARCHAR(20)   COMMENT 'INDEX/STAGE/EVENT/APPEAL',
    stage_code      VARCHAR(50),
    index_code      VARCHAR(50),
    index_name      VARCHAR(200),
    sn              INT,
    weight          INT,
    interval_weight DECIMAL(10,4) COMMENT '区间权重',
    data_value      VARCHAR(255)  COMMENT '实际值',
    score           DECIMAL(10,2) COMMENT '得分',
    standard_score  DECIMAL(10,2) COMMENT '标准纯得分',
    stage_score     DECIMAL(10,4) COMMENT 'stage 聚合得分',
    score_mode      VARCHAR(32)   COMMENT '评分模式',
    dimension_rule  TEXT          COMMENT '业务规则',
    is_red_line     VARCHAR(8),
    risk_level      VARCHAR(20),
    priority        INT,
    evaluate_instance VARCHAR(50),
    status          VARCHAR(20),
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_object_log (object_log_id),
    INDEX idx_task_log (task_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估指标日志';

CREATE TABLE eval_indicator_log_msg (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    indicator_log_id BIGINT       COMMENT '关联 eval_indicator_log',
    param_in        TEXT          COMMENT '入参（大字段分离）',
    param_out       TEXT          COMMENT '出参（大字段分离）',
    error_msg       TEXT          COMMENT '错误消息（大字段分离）',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估指标日志消息（大字段分离）';

CREATE TABLE eval_event_log (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    object_log_id   BIGINT        NOT NULL COMMENT '关联 eval_object_log',
    task_log_id     BIGINT,
    biz_id          VARCHAR(60)   COMMENT '业务对象标识',
    scene_code      VARCHAR(50),
    model_code      VARCHAR(50),
    sn              INT           NOT NULL,
    event_code      VARCHAR(50)   NOT NULL,
    event_name      VARCHAR(200),
    event_type      VARCHAR(20)   NOT NULL COMMENT 'RED_LINE/MARK/BONUS/DEDUCT',
    dimension_rule  TEXT          COMMENT '触发条件',
    score_before    DECIMAL(10,2),
    score_after     DECIMAL(10,2),
    event_score     DECIMAL(10,2),
    red_line_message VARCHAR(500),
    trigger_values  TEXT          COMMENT '触发时字段快照 JSON',
    is_red_line     CHAR(1)       DEFAULT 'N',
    status          VARCHAR(20)   DEFAULT 'SUCCESS',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_object_log (object_log_id),
    INDEX idx_task_log (task_log_id),
    INDEX idx_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估事件日志';

-- ========================================
-- 7. 申诉 + 等级 (3 张表)
-- ========================================

CREATE TABLE eval_appeal_header (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    appeal_no       VARCHAR(32)   NOT NULL COMMENT '申诉编号',
    appeal_type     VARCHAR(20)   NOT NULL COMMENT 'BONUS/PENALTY/TOTAL/DIMENSION',
    scene_id        BIGINT        NOT NULL,
    object_id       BIGINT        COMMENT '申诉对象',
    data_period     VARCHAR(50),
    score_adjustment DECIMAL(10,2) COMMENT '加减分值',
    adjusted_total_score DECIMAL(10,2),
    reason          TEXT          COMMENT '申诉原因',
    attachment_urls TEXT          COMMENT '佐证材料 JSON',
    status          VARCHAR(20)   DEFAULT 'PENDING' COMMENT 'PENDING/EXECUTED',
    submit_time     DATETIME,
    execute_time    DATETIME,
    batch_no        VARCHAR(32)   COMMENT '批量导入批次号',
    task_log_id     BIGINT        COMMENT '关联评估任务',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_appeal_no (appeal_no),
    INDEX idx_scene_status (scene_id, status),
    INDEX idx_object (object_id),
    INDEX idx_batch (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申诉主表';

CREATE TABLE eval_appeal_detail (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    appeal_id       BIGINT        NOT NULL,
    object_id       BIGINT        NOT NULL,
    dimension_id    BIGINT        COMMENT '维度ID(DIMENSION类型用, MVP留空)',
    score_adjustment DECIMAL(10,2),
    reason          TEXT,
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_appeal (appeal_id),
    INDEX idx_object (object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申诉明细';

CREATE TABLE eval_grade_mapping (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
    scene_id        BIGINT        NOT NULL,
    mapping_mode    VARCHAR(20)   DEFAULT 'SCORE_RANGE' COMMENT 'SCORE_RANGE/RANK_PERCENT',
    group_fields    VARCHAR(500)  COMMENT '分组字段(RANK_PERCENT)',
    order_by_fields VARCHAR(500)  COMMENT '排序字段(RANK_PERCENT)',
    rank_direction  VARCHAR(10)   DEFAULT 'DESC',
    grade           VARCHAR(4)    NOT NULL COMMENT 'S/A/B/C/D/E',
    lower_bound     DECIMAL(10,2) COMMENT '分数下限(含)',
    upper_bound     DECIMAL(10,2) COMMENT '分数上限(含)',
    priority        INT           DEFAULT 0,
    memo            VARCHAR(100)  COMMENT '等级说明',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scene (scene_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='等级映射';
