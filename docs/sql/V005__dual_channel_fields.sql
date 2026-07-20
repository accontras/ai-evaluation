-- V005: eval_indicator_log 扩展双通道对比字段
ALTER TABLE eval_indicator_log
    ADD COLUMN llm_score   DECIMAL(10,2) COMMENT 'LLM 打分',
    ADD COLUMN rule_score  DECIMAL(10,2) COMMENT '规则引擎打分',
    ADD COLUMN score_diff  DECIMAL(10,2) COMMENT 'LLM vs 规则 差异',
    ADD COLUMN diff_level  VARCHAR(20)   COMMENT '差异分级: TRIVIAL/NOTABLE/SIGNIFICANT',
    ADD COLUMN llm_reason  VARCHAR(500)  COMMENT 'LLM 打分理由';
