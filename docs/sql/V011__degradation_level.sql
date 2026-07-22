-- V011: A4 降级层级追踪 (MySQL 8 不支持 ADD COLUMN IF NOT EXISTS, 先检查)
-- SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS
--   WHERE TABLE_SCHEMA='eval_db' AND TABLE_NAME='eval_ai_experiment' AND COLUMN_NAME='degradation_level';
-- 简化: 直接加, 重复执行报错可忽略
ALTER TABLE eval_ai_experiment ADD COLUMN degradation_level VARCHAR(16)
  DEFAULT 'NONE' COMMENT '降级层级: NONE/L1_FALLBACK/L2_RULE/L3_DEFAULT';
