-- V006: eval_model_stage 加 route_condition 字段 (S16 top 路由)
ALTER TABLE eval_model_stage
    ADD COLUMN route_condition VARCHAR(500) COMMENT 'TOP 路由 JEXL 条件，如: attrValues["dept"] == "R&D"';
