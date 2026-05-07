-- 敏感词表
CREATE TABLE IF NOT EXISTS sensitive_words (
    id              BIGSERIAL       PRIMARY KEY,
    word            VARCHAR(128)    NOT NULL,
    category        VARCHAR(32)     NOT NULL,           -- POLITICS/PORNOGRAPHY/VIOLENCE/FRAUD/CUSTOM
    action          VARCHAR(16)     NOT NULL DEFAULT 'BLOCK',  -- BLOCK/WARN/MASK
    enabled         SMALLINT        NOT NULL DEFAULT 1,
    created_by      BIGINT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sensitive_words_category ON sensitive_words(category);
CREATE INDEX IF NOT EXISTS idx_sensitive_words_enabled ON sensitive_words(enabled);

-- 违规记录表
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
    direction       VARCHAR(8)      NOT NULL,           -- INPUT/OUTPUT
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_violation_logs_time ON violation_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_violation_logs_apikey ON violation_logs(api_key_id, created_at DESC);

-- 初始化敏感词示例数据
INSERT INTO sensitive_words (word, category, action, enabled, created_at, updated_at) VALUES
('赌博', 'FRAUD', 'BLOCK', 1, NOW(), NOW()),
('彩票预测', 'FRAUD', 'BLOCK', 1, NOW(), NOW()),
('内幕消息', 'FRAUD', 'BLOCK', 1, NOW(), NOW()),
('股票推荐', 'FRAUD', 'WARN', 1, NOW(), NOW()),
('理财建议', 'FRAUD', 'WARN', 1, NOW(), NOW())
ON CONFLICT DO NOTHING;
