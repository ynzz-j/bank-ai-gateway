-- 令牌桶限流 Lua 脚本
-- KEYS[1]: 限流键
-- ARGV[1]: 桶容量(capacity)
-- ARGV[2]: 每秒生成令牌数(rate)
-- ARGV[3]: 当前时间戳(毫秒, now)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])  -- 每秒令牌数
local now = tonumber(ARGV[3])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    last_refill = now
end

-- 计算需要补充的令牌
local elapsed = (now - last_refill) / 1000.0
local refill = math.floor(elapsed * rate)
if refill > 0 then
    tokens = math.min(capacity, tokens + refill)
    last_refill = now
end

if tokens >= 1 then
    tokens = tokens - 1
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
    redis.call('EXPIRE', key, math.ceil(capacity / rate))
    return {1, capacity, tokens, now}
else
    return {0, capacity, 0, last_refill + math.ceil((1 - tokens) / rate * 1000)}
end
