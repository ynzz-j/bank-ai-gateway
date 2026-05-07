-- 滑动窗口限流 Lua 脚本（基于 Redis ZSET）
-- KEYS[1]: 限流键
-- ARGV[1]: 限制数量(limit)
-- ARGV[2]: 窗口大小(毫秒, window)
-- ARGV[3]: 当前时间戳(毫秒, now)

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local window_start = now - window

-- 清理过期记录
redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

-- 获取当前计数
local current = redis.call('ZCARD', key)

if current < limit then
    -- 添加新记录（用随机数避免时间戳冲突）
    redis.call('ZADD', key, now, now .. ':' .. math.random(1000000))
    redis.call('EXPIRE', key, math.ceil(window / 1000))
    local newCount = redis.call('ZCARD', key)
    return {1, limit, limit - newCount, now + window}
else
    local ttl = redis.call('TTL', key)
    local reset = now + (ttl * 1000)
    return {0, limit, 0, reset}
end
