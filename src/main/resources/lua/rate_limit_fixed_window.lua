-- 固定窗口限流 Lua 脚本
-- KEYS[1]: 限流键
-- ARGV[1]: 限制数量(limit)
-- ARGV[2]: 窗口大小(毫秒, window)
-- ARGV[3]: 当前时间戳(毫秒, now)

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local current = redis.call('GET', key)
if current == false then
    current = 0
end

if tonumber(current) < limit then
    redis.call('INCR', key)
    redis.call('EXPIRE', key, math.ceil(window / 1000))
    local newVal = redis.call('GET', key)
    return {1, limit, limit - tonumber(newVal), now + window}
else
    local ttl = redis.call('TTL', key)
    local reset = now + (ttl * 1000)
    return {0, limit, 0, reset}
end
