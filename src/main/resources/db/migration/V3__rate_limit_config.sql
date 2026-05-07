-- 限流配置表
-- 存储 API Key、用户、IP 等维度的限流配置

CREATE TABLE IF NOT EXISTS rate_limit_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    dimension VARCHAR(16) NOT NULL COMMENT '限流维度（API_KEY/USER/IP）',
    dimension_value VARCHAR(128) NOT NULL COMMENT '维度值（API Key ID/用户ID/IP地址）',
    limit_config VARCHAR(32) NOT NULL COMMENT '限制配置（格式：100/min, 1000/hour）',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用（0-禁用，1-启用）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_dimension_value (dimension, dimension_value),
    INDEX idx_dimension (dimension),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='限流配置表';

-- 初始数据：全局默认限流配置
INSERT INTO rate_limit_config (dimension, dimension_value, limit_config, enabled) VALUES
('IP', 'global', '100/min', 1),
('USER', 'global', '200/min', 1),
('API_KEY', 'global', '500/min', 1);
