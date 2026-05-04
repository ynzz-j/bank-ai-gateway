-- 模型映射表
-- 存储统一模型名到各渠道实际模型名的映射关系

CREATE TABLE IF NOT EXISTS model_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    unified_model VARCHAR(64) NOT NULL COMMENT '统一模型名（用户请求的模型名）',
    provider VARCHAR(32) NOT NULL COMMENT '渠道提供商（OPENAI/CLAUDE/BAIDU/QWEN/ZHIPU/KIMI）',
    actual_model VARCHAR(128) NOT NULL COMMENT '渠道实际模型名',
    priority INT DEFAULT 100 COMMENT '优先级（数字越小优先级越高）',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用（0-禁用，1-启用）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_unified_provider (unified_model, provider),
    INDEX idx_unified_model (unified_model),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型映射表';

-- 初始数据：常用模型映射
INSERT INTO model_mapping (unified_model, provider, actual_model, priority, enabled) VALUES
-- GPT-4 系列
('gpt-4', 'OPENAI', 'gpt-4-turbo', 10, 1),
('gpt-4', 'CLAUDE', 'claude-3-opus-20240229', 20, 1),
('gpt-4o', 'OPENAI', 'gpt-4o', 10, 1),
('gpt-4o-mini', 'OPENAI', 'gpt-4o-mini', 10, 1),
('gpt-3.5-turbo', 'OPENAI', 'gpt-3.5-turbo', 10, 1),

-- Claude 系列
('claude-3-opus', 'CLAUDE', 'claude-3-opus-20240229', 10, 1),
('claude-3-sonnet', 'CLAUDE', 'claude-3-sonnet-20240229', 10, 1),
('claude-3-haiku', 'CLAUDE', 'claude-3-haiku-20240307', 10, 1),
('claude-3-5-sonnet', 'CLAUDE', 'claude-3-5-sonnet-20241022', 10, 1),

-- 文心一言系列
('ernie-4', 'BAIDU', 'ernie-4.0-8k', 10, 1),
('ernie-3.5', 'BAIDU', 'ernie-3.5-8k', 10, 1),
('ernie-lite', 'BAIDU', 'ernie-lite-8k', 10, 1),

-- 通义千问系列
('qwen-plus', 'QWEN', 'qwen-plus', 10, 1),
('qwen-turbo', 'QWEN', 'qwen-turbo', 10, 1),
('qwen-max', 'QWEN', 'qwen-max', 20, 1),
('qwen-long', 'QWEN', 'qwen-long', 10, 1),

-- 智谱 GLM 系列
('glm-4', 'ZHIPU', 'glm-4', 10, 1),
('glm-4-plus', 'ZHIPU', 'glm-4-plus', 10, 1),
('glm-4-flash', 'ZHIPU', 'glm-4-flash', 10, 1),
('glm-3-turbo', 'ZHIPU', 'glm-3-turbo', 10, 1),

-- Kimi 系列
('kimi-k2', 'KIMI', 'moonshot-v1-8k', 10, 1),
('kimi-k1.5', 'KIMI', 'moonshot-v1-8k', 10, 1),
('kimi-k1', 'KIMI', 'moonshot-v1-8k', 20, 1);
