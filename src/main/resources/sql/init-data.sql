-- =====================================================
-- 银行AI网关 - 初始数据
-- 版本: 1.0.0
-- =====================================================

-- 设置时区
SET TIME ZONE 'Asia/Shanghai';

-- =====================================================
-- 1. 默认管理员账号
-- =====================================================
-- 用户名: admin
-- 密码: Admin@123 (bcrypt hash, cost=12)
-- 注意：生产环境必须修改密码！
INSERT INTO users (username, password_hash, role, status)
VALUES (
    'admin',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4.G.4oF.C8dqCXaG',
    'ADMIN',
    1
) ON CONFLICT (username) DO NOTHING;

-- =====================================================
-- 2. 系统配置默认值
-- =====================================================
INSERT INTO configs (config_key, config_value, description, value_type) VALUES
    ('global.max_rpm', '1000', '全局每分钟最大请求数', 'INTEGER'),
    ('global.max_tpm', '1000000', '全局每分钟最大Token数', 'INTEGER'),
    ('channel.health_check.enabled', 'true', '渠道健康检查开关', 'BOOLEAN'),
    ('channel.health_check.interval', '300', '渠道健康检查间隔(秒)', 'INTEGER'),
    ('compliance.disclaimer.enabled', 'true', '风险提示开关', 'BOOLEAN'),
    ('compliance.disclaimer.template', '【风险提示】本回复由人工智能生成，仅供参考，不构成任何投资建议或专业意见。银行不对AI生成内容的准确性、完整性和可靠性承担责任。如需专业服务，请咨询相关专业人士。', '免责声明模板', 'STRING'),
    ('audit.log_request_body', 'true', '审计日志是否记录请求体', 'BOOLEAN'),
    ('audit.log_response_body', 'false', '审计日志是否记录响应体', 'BOOLEAN')
ON CONFLICT (config_key) DO NOTHING;

-- =====================================================
-- 3. 默认敏感词（示例）
-- =====================================================
INSERT INTO sensitive_words (word, category, action, filter_type) VALUES
    -- 诈骗相关
    ('验证码转账', 'FRAUD', 'BLOCK', 'KEYWORD'),
    ('银行卡密码', 'FRAUD', 'BLOCK', 'KEYWORD'),
    ('短信验证码给我', 'FRAUD', 'BLOCK', 'KEYWORD'),
    
    -- 合规红线
    ('内幕消息', 'COMPLIANCE', 'BLOCK', 'KEYWORD'),
    ('老鼠仓', 'COMPLIANCE', 'BLOCK', 'KEYWORD'),
    
    -- 隐私信息（正则模式，由程序处理，此处仅示例）
    ('身份证号', 'PRIVACY', 'MASK', 'KEYWORD'),
    ('银行卡号', 'PRIVACY', 'MASK', 'KEYWORD')
ON CONFLICT DO NOTHING;

-- =====================================================
-- 4. 示例渠道配置（仅开发环境，生产环境需删除）
-- =====================================================
-- 注意：这里的 api_key_encrypted 是占位符，实际需要加密后存储
-- INSERT INTO channels (name, provider, base_url, api_key_encrypted, api_key_nonce, api_key_kms_version, models, priority, weight)
-- VALUES 
--     ('OpenAI Official', 'OPENAI', 'https://api.openai.com', '', '', 'v1', 'gpt-4,gpt-4o,gpt-3.5-turbo', 100, 1),
--     ('Claude Official', 'CLAUDE', 'https://api.anthropic.com', '', '', 'v1', 'claude-3-opus,claude-3-sonnet', 90, 1);

-- =====================================================
-- 完成
-- =====================================================
-- 初始数据导入完成