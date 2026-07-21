-- V008: AI 实验记录表 — A2 LLM 可观测性
CREATE TABLE IF NOT EXISTS eval_ai_experiment (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    experiment_type VARCHAR(32)  NOT NULL COMMENT 'SCORING / EVENT / SUMMARY',
    model           VARCHAR(64)  COMMENT '模型名称',
    prompt_version  VARCHAR(32)  COMMENT 'Prompt 版本标识',
    scene_code      VARCHAR(64),
    biz_id          VARCHAR(64),
    index_code      VARCHAR(64),
    input_tokens    INT          COMMENT '输入 token 数',
    output_tokens   INT          COMMENT '输出 token 数',
    duration_ms     BIGINT       COMMENT '调用耗时(毫秒)',
    llm_score       DECIMAL(10,2) COMMENT 'LLM 打分',
    rule_score      DECIMAL(10,2) COMMENT '规则引擎打分(对比基线)',
    score_diff      DECIMAL(10,2) COMMENT 'LLM vs 规则差异',
    temperature     DECIMAL(3,2) COMMENT 'LLM temperature',
    error_type      VARCHAR(64)  COMMENT '错误类型: null=成功',
    retry_count     INT DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scene (scene_code),
    INDEX idx_type (experiment_type),
    INDEX idx_model (model),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI实验记录';
