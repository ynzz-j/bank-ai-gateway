-- ===========================================================
-- Sprint 5: 审计日志与调用日志表
-- 创建时间: 2026-05-04
-- 说明: 
--   - audit_logs: 审计日志（只增不改不删）
--   - call_logs: 调用日志（按月分区）
--   - daily_stats: 每日统计聚合
-- ===========================================================

-- 1. 审计日志表
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    
    -- 操作信息
    user_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/DISABLE/ENABLE/ROTATE',
    
    -- 资源信息
    resource_type VARCHAR(32) NOT NULL COMMENT 'API_KEY/CHANNEL/MODEL_MAPPING/CONFIG/SENSITIVE_WORD',
    resource_id BIGINT,
    resource_name VARCHAR(128),
    
    -- 变更详情（JSON格式）
    change_snapshot JSONB,
    
    -- 请求信息
    request_ip VARCHAR(64),
    user_agent VARCHAR(256),
    request_id VARCHAR(64),
    
    -- 时间戳
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 索引
    CONSTRAINT uk_audit_resource_action UNIQUE (resource_type, resource_id, action, created_at)
);

CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_resource_type ON audit_logs(resource_type);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at DESC);

COMMENT ON TABLE audit_logs IS '审计日志表 - 记录所有管理操作，只增不改不删';
COMMENT ON COLUMN audit_logs.change_snapshot IS '变更前后快照，JSON格式';

-- 2. 调用日志表（按月分区）
CREATE TABLE IF NOT EXISTS call_logs (
    id BIGSERIAL,
    api_key_id BIGINT NOT NULL,
    api_key_name VARCHAR(128),
    user_id BIGINT,
    
    -- 模型信息
    model VARCHAR(64) NOT NULL,
    provider VARCHAR(32),
    actual_model VARCHAR(64),
    
    -- 请求信息
    request_id VARCHAR(128) NOT NULL,
    input_content TEXT,
    input_tokens INT,
    
    -- 响应信息
    output_content TEXT,
    output_tokens INT,
    total_tokens INT,
    
    -- 性能指标
    latency_ms INT,
    first_token_latency_ms INT,
    
    -- 渠道信息
    channel_id BIGINT,
    channel_name VARCHAR(64),
    
    -- 响应质量
    finish_reason VARCHAR(32),
    error_code VARCHAR(32),
    error_message TEXT,
    
    -- 请求上下文
    client_ip VARCHAR(64),
    user_agent VARCHAR(256),
    
    -- 时间戳
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 创建分区函数
CREATE OR REPLACE FUNCTION create_call_log_partition(partition_date DATE)
RETURNS void AS $$
DECLARE
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    partition_name := 'call_logs_' || to_char(partition_date, 'YYYY_MM');
    start_date := date_trunc('month', partition_date);
    end_date := start_date + interval '1 month';
    
    -- 检查分区是否已存在
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = partition_name
        AND n.nspname = 'public'
    ) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF call_logs FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
        RAISE NOTICE 'Created partition: %', partition_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- 创建默认分区（未来月份）
CREATE TABLE IF NOT EXISTS call_logs_default PARTITION OF call_logs DEFAULT;

-- 创建索引
CREATE INDEX idx_call_api_key ON call_logs(api_key_id);
CREATE INDEX idx_call_user ON call_logs(user_id);
CREATE INDEX idx_call_model ON call_logs(model);
CREATE INDEX idx_call_request_id ON call_logs(request_id);
CREATE INDEX idx_call_created_at ON call_logs(created_at DESC);

COMMENT ON TABLE call_logs IS '调用日志表 - 按月分区，自动归档';

-- 3. 每日统计表
CREATE TABLE IF NOT EXISTS daily_stats (
    id BIGSERIAL PRIMARY KEY,
    stat_date DATE NOT NULL,
    
    -- 维度
    api_key_id BIGINT,
    api_key_name VARCHAR(128),
    user_id BIGINT,
    model VARCHAR(64),
    provider VARCHAR(32),
    
    -- 统计数据
    total_requests INT DEFAULT 0,
    successful_requests INT DEFAULT 0,
    failed_requests INT DEFAULT 0,
    total_input_tokens BIGINT DEFAULT 0,
    total_output_tokens BIGINT DEFAULT 0,
    total_latency_ms BIGINT DEFAULT 0,
    avg_latency_ms INT DEFAULT 0,
    
    -- 元信息
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 唯一约束
    CONSTRAINT uk_daily_stats UNIQUE (stat_date, api_key_id, user_id, model, provider)
);

CREATE INDEX idx_daily_stats_date ON daily_stats(stat_date DESC);
CREATE INDEX idx_daily_stats_api_key ON daily_stats(api_key_id);
CREATE INDEX idx_daily_stats_model ON daily_stats(model);

COMMENT ON TABLE daily_stats IS '每日统计表 - 按天聚合关键指标';