-- token_bucket.lua
-- Redis令牌桶限流脚本
--
-- KEYS[1]: 限流Key
-- ARGV[1]: 桶容量 (capacity)
-- ARGV[2]: 每秒填充速率 (rate)
-- ARGV[3]: 请求数量 (requested)
-- ARGV[4]: 当前时间戳毫秒 (now)
--
-- 返回: 1=允许, 0=拒绝

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

-- 获取当前令牌数和上次更新时间
local tokens = tonumber(redis.call('hget', key, 'tokens') or capacity)
local last_time = tonumber(redis.call('hget', key, 'last_time') or now)

-- 计算补充的令牌
local elapsed = (now - last_time) / 1000
local new_tokens = math.min(capacity, tokens + elapsed * rate)

local result
if new_tokens >= requested then
    -- 允许请求，扣除令牌
    new_tokens = new_tokens - requested
    result = 1
else
    -- 拒绝请求
    result = 0
end

-- 更新状态
redis.call('hset', key, 'tokens', new_tokens)
redis.call('hset', key, 'last_time', now)
redis.call('expire', key, math.ceil(capacity / rate) + 1)

return result