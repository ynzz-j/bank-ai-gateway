-- =====================================================
-- 银行AI网关 - 数据库初始化脚本
-- 版本: 1.0.0
-- 数据库: PostgreSQL 16
-- =====================================================

-- 设置时区
SET TIME ZONE 'Asia/Shanghai';

-- =====================================================
-- 1. 用户表
-- =====================================================
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(64)     UNIQUE NOT NULL,
    password_hash   VARCHAR(128)    NOT NULL,
    role            VARCHAR(16)     NOT NULL DEFAULT 'USER',
    status          SMALLINT        NOT NULL DEFAULT 1,
    login_fail_count SMALLINT       NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP,
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.id IS '主键ID';
COMMENT ON COLUMN users.username IS '用户名';
COMMENT ON COLUMN users.password_hash IS '密码哈希(bcrypt)';
COMMENT ON COLUMN users.role IS '角色: ADMIN/USER';
COMMENT ON COLUMN users.status IS '状态: 1启用 0禁用';
COMMENT ON COLUMN users.login_fail_count IS '连续登录失败次数';
COMMENT ON COLUMN users.locked_until IS '锁定截止时间';

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username) WHERE status = 1;

-- =====================================================
-- 2. API Key表
-- =====================================================
CREATE TABLE IF NOT EXISTS api_keys (
    id              BIGSERIAL       PRIMARY KEY,
    key_prefix      VARCHAR(12)     NOT NULL,
    key_hash        VARCHAR(64)     NOT NULL,
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    name            VARCHAR(64),
    quota_rpm       INTEGER         DEFAULT 60,
    quota_tpm       INTEGER         DEFAULT 100000,
    quota_total     BIGINT          DEFAULT -1,
    used_quota      BIGINT          DEFAULT 0,
    status          SMALLINT        NOT NULL DEFAULT 1,
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE api_keys IS 'API Key表';
COMMENT ON COLUMN api_keys.key_prefix IS 'Key前缀(明文)，用于快速定位';
COMMENT ON COLUMN api_keys.key_hash IS '完整Key的SHA256哈希';
COMMENT ON COLUMN api_keys.quota_rpm IS '每分钟请求数配额，-1无限';
COMMENT ON COLUMN api_keys.quota_tpm IS '每分钟Token数配额，-1无限';
COMMENT ON COLUMN api_keys.quota_total IS '总配额(Token数)，-1无限';
COMMENT ON COLUMN api_keys.status IS '状态: 1启用 0禁用 2已轮换';

CREATE UNIQUE INDEX IF NOT EXISTS idx_api_keys_prefix ON api_keys(key_prefix);
CREATE INDEX IF NOT EXISTS idx_api_keys_user ON api_keys(user_id, status);

-- =====================================================
-- 3. AI渠道表
-- =====================================================
CREATE TABLE IF NOT EXISTS channels (
    id                  BIGSERIAL       PRIMARY KEY,
    name                VARCHAR(64)     NOT NULL,
    provider            VARCHAR(32)     NOT NULL,
    base_url            VARCHAR(256),
    api_key_encrypted   TEXT            NOT NULL,
    api_key_nonce       VARCHAR(32)     NOT NULL,
    api_key_kms_version VARCHAR(16)     NOT NULL,
    models              TEXT            NOT NULL,
    priority            INTEGER         NOT NULL DEFAULT 0,
    weight              INTEGER         NOT NULL DEFAULT 1,
    status              SMALLINT        NOT NULL DEFAULT 1,
    health_status       SMALLINT        NOT NULL DEFAULT 1,
    last_health_check   TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE channels IS 'AI渠道表';
COMMENT ON COLUMN channels.provider IS '提供商: OPENAI/CLAUDE/WENXIN/TONGYI/ZHIPU/KIMI';
COMMENT ON COLUMN channels.api_key_encrypted IS 'API Key(AES-256-GCM加密)';
COMMENT ON COLUMN channels.priority IS '优先级，越大越优先';
COMMENT ON COLUMN channels.weight IS '权重，同优先级按权重分配';
COMMENT ON COLUMN channels.health_status IS '健康状态: 1健康 2熔断 3不可用';

CREATE INDEX IF NOT EXISTS idx_channels_provider_status ON channels(provider, status);
CREATE INDEX IF NOT EXISTS idx_channels_status_priority ON channels(status DESC, priority DESC);

-- =====================================================
-- 4. 模型映射表
-- =====================================================
CREATE TABLE IF NOT EXISTS model_mappings (
    id              BIGSERIAL       PRIMARY KEY,
    unified_name    VARCHAR(64)     NOT NULL,
    channel_id      BIGINT          NOT NULL REFERENCES channels(id),
    provider_model  VARCHAR(64)     NOT NULL,
    status          SMALLINT        NOT NULL DEFAULT 1,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE(unified_name, channel_id)
);

COMMENT ON TABLE model_mappings IS '模型映射表';
COMMENT ON COLUMN model_mappings.unified_name IS '统一模型名，如gpt-4';
COMMENT ON COLUMN model_mappings.provider_model IS '渠道内模型名';

CREATE INDEX IF NOT EXISTS idx_model_mappings_unified ON model_mappings(unified_name, status);

-- =====================================================
-- 5. 敏感词表
-- =====================================================
CREATE TABLE IF NOT EXISTS sensitive_words (
    id                  BIGSERIAL       PRIMARY KEY,
    word                VARCHAR(128)    NOT NULL,
    category            VARCHAR(32)     NOT NULL,
    action              VARCHAR(16)     NOT NULL DEFAULT 'BLOCK',
    whitelist_context   VARCHAR(256),
    combo_rule          TEXT,
    filter_type         VARCHAR(16)     NOT NULL DEFAULT 'KEYWORD',
    status              SMALLINT        NOT NULL DEFAULT 1,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE sensitive_words IS '敏感词表';
COMMENT ON COLUMN sensitive_words.category IS '分类: FRAUD/COMPLIANCE/PRIVACY/RISK/POLITICAL';
COMMENT ON COLUMN sensitive_words.action IS '动作: BLOCK/WARN/MASK';
COMMENT ON COLUMN sensitive_words.whitelist_context IS '白名单上下文(JSON数组)';
COMMENT ON COLUMN sensitive_words.combo_rule IS '组合规则';
COMMENT ON COLUMN sensitive_words.filter_type IS '过滤类型: KEYWORD/REGEX';

CREATE INDEX IF NOT EXISTS idx_sensitive_words_category ON sensitive_words(category, status);

-- =====================================================
-- 6. 调用日志表（按月分区）
-- =====================================================
CREATE TABLE IF NOT EXISTS call_logs (
    id              BIGSERIAL,
    api_key_id      BIGINT,
    user_id         BIGINT,
    channel_id      BIGINT,
    model           VARCHAR(64),
    unified_model   VARCHAR(64),
    input_tokens    INTEGER         DEFAULT 0,
    output_tokens   INTEGER         DEFAULT 0,
    latency_ms      INTEGER,
    status_code     SMALLINT,
    error_message   VARCHAR(512),
    is_stream       BOOLEAN         DEFAULT FALSE,
    is_filtered     BOOLEAN         DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE call_logs IS '调用日志表（按月分区）';

-- 创建初始分区
CREATE TABLE IF NOT EXISTS call_logs_2026_05 PARTITION OF call_logs
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE IF NOT EXISTS call_logs_2026_06 PARTITION OF call_logs
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

-- =====================================================
-- 7. 违规记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS violation_logs (
    id              BIGSERIAL       PRIMARY KEY,
    api_key_id      BIGINT,
    user_id         BIGINT,
    call_log_id     BIGINT,
    filter_name     VARCHAR(32),
    matched_word    VARCHAR(128),
    category        VARCHAR(32),
    action          VARCHAR(16)     NOT NULL,
    content_snippet VARCHAR(512),
    direction       VARCHAR(8)      NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE violation_logs IS '违规记录表';
COMMENT ON COLUMN violation_logs.direction IS '方向: INPUT/OUTPUT';

CREATE INDEX IF NOT EXISTS idx_violation_logs_time ON violation_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_violation_logs_apikey ON violation_logs(api_key_id, created_at DESC);

-- =====================================================
-- 8. 审计日志表
-- =====================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGSERIAL       PRIMARY KEY,
    operator_id     BIGINT          NOT NULL REFERENCES users(id),
    operator_name   VARCHAR(64)     NOT NULL,
    action          VARCHAR(32)     NOT NULL,
    resource_type   VARCHAR(32)     NOT NULL,
    resource_id     BIGINT,
    detail          JSONB,
    ip_address      INET,
    user_agent      VARCHAR(256),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE audit_logs IS '审计日志表（只增不改不删）';
COMMENT ON COLUMN audit_logs.action IS '操作: CREATE/UPDATE/DELETE/LOGIN/LOGIN_FAIL/ENABLE/DISABLE';
COMMENT ON COLUMN audit_logs.resource_type IS '资源类型: API_KEY/CHANNEL/CONFIG/USER/SESSION';
COMMENT ON COLUMN audit_logs.detail IS '操作详情(JSONB，含变更前后快照)';

CREATE INDEX IF NOT EXISTS idx_audit_operator_time ON audit_logs(operator_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_action_time ON audit_logs(action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_resource ON audit_logs(resource_type, resource_id, created_at DESC);

-- =====================================================
-- 9. 系统配置表
-- =====================================================
CREATE TABLE IF NOT EXISTS configs (
    id              BIGSERIAL       PRIMARY KEY,
    config_key      VARCHAR(64)     UNIQUE NOT NULL,
    config_value    TEXT,
    description     VARCHAR(256),
    value_type      VARCHAR(16)     DEFAULT 'STRING',
    updated_by      BIGINT          REFERENCES users(id),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE configs IS '系统配置表';
COMMENT ON COLUMN configs.value_type IS '值类型: STRING/INTEGER/BOOLEAN/JSON';

-- =====================================================
-- 10. 每日配额统计表
-- =====================================================
CREATE TABLE IF NOT EXISTS daily_stats (
    id              BIGSERIAL       PRIMARY KEY,
    stat_date       DATE            NOT NULL,
    user_id         BIGINT          NOT NULL,
    api_key_id      BIGINT          NOT NULL,
    request_count   INTEGER         DEFAULT 0,
    input_tokens    BIGINT          DEFAULT 0,
    output_tokens   BIGINT          DEFAULT 0,
    violation_count INTEGER         DEFAULT 0,
    UNIQUE(stat_date, user_id, api_key_id)
);

COMMENT ON TABLE daily_stats IS '每日配额统计表';

CREATE INDEX IF NOT EXISTS idx_daily_stats_date ON daily_stats(stat_date DESC);
CREATE INDEX IF NOT EXISTS idx_daily_stats_user_date ON daily_stats(user_id, stat_date DESC);

-- =====================================================
-- 完成
-- =====================================================
-- 全部10张表创建完成