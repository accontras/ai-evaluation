-- V010: RAG 检索对比日志表 — A3.3 检索质量评测
CREATE TABLE IF NOT EXISTS eval_rag_compare_log (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_id                VARCHAR(64)   COMMENT '评估对象ID',
    scene_code            VARCHAR(64)   COMMENT '场景编码',
    index_code            VARCHAR(64)   COMMENT '指标编码',
    index_name            VARCHAR(128)  COMMENT '指标名称',
    data_value            VARCHAR(255)  COMMENT '指标实际值',
    vector_results        JSON          COMMENT '向量检索返回的logId列表 [id1,id2,id3]',
    rule_results          JSON          COMMENT '规则检索返回的logId列表 [id1,id2,id3]',
    vector_similarities   JSON          COMMENT '向量检索相似度 [0.85,0.72,0.61]',
    vector_hit            TINYINT(1)    COMMENT '向量是否有返回',
    rule_hit              TINYINT(1)    COMMENT '规则是否有返回',
    ground_truth_rel      JSON          COMMENT '并行数组相关性标记 [1,0,0] (评测模式, 运行时NULL)',
    created_at            DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scene_biz (scene_code, biz_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 检索对比日志';
