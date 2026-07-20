-- S9 端到端测试数据: 物流费用合理性评估模型
INSERT INTO eval_model (id, code, name, aggregate_mode, status, enabled) VALUES
(1, 'LOGISTICS_COST', '物流费用合理性评估', 'weighted_sum', 'ENABLED', 1);

INSERT INTO eval_model_stage (id, model_id, code, name, type, sn, weight, enabled) VALUES
(1, 1, 'COST', '费用维度', 'normal', 1, 100, 1);

INSERT INTO eval_index (id, code, name, dimensions, index_field_code, enabled) VALUES
(1, 'COST_DEV', '费用偏差率', '["cost_deviation"]', 'cost_deviation', 1),
(2, 'ABNORM_CNT', '异常波动次数', '["abnormal_count"]', 'abnormal_count', 1),
(3, 'FILL_RATE', '填报及时率', '["fill_rate"]', 'fill_rate', 1);

INSERT INTO eval_model_index (id, model_id, stage_id, index_id, sn, enabled) VALUES
(1, 1, 1, 1, 1, 1),
(2, 1, 1, 2, 2, 1),
(3, 1, 1, 3, 3, 1);

INSERT INTO eval_scene (id, code, model_id, name, status, enabled) VALUES
(1, 'LOGISTICS-2026Q2', 1, '物流费用评估-2026Q2', 'PUBLISHED', 1);
