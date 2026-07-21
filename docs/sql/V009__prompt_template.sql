-- V009: Prompt 版本管理表 — A1.2
CREATE TABLE IF NOT EXISTS eval_prompt_template (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    prompt_key      VARCHAR(64)  NOT NULL COMMENT 'SCORING_SYSTEM / SCORING_USER / EVENT_DETECT / SUMMARY_R1',
    version         VARCHAR(32)  NOT NULL COMMENT 'v1-base / v2-standards / v3-fewshot',
    system_text     TEXT         NOT NULL COMMENT 'System Prompt',
    user_text       TEXT         NOT NULL COMMENT 'User Prompt 模板',
    description     VARCHAR(500) COMMENT '版本说明/changelog',
    is_active       TINYINT DEFAULT 1 COMMENT '是否启用',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_key_version (prompt_key, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt 版本管理';
