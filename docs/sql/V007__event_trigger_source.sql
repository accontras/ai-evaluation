-- V007: eval_event_log 加 trigger_source 字段 (S17 双通道事件)
ALTER TABLE eval_event_log
    ADD COLUMN trigger_source VARCHAR(10) COMMENT '触发来源: RULE / LLM / BOTH';
