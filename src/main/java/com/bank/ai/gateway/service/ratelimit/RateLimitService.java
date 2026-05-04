package com.bank.ai.gateway.service.ratelimit;

import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * 限流服务
 *
 * <p>基于 Redis + Lua 脚本实现分布式限流，支持多维度限流策略。
 *
 * <p>限流维度：
 * <ul>
 *   <li>API Key 维度：限制每个 API Key 的请求频率</li>
 *   <li>用户维度：限制每个用户的请求频率</li>
 *   <li>IP 维度：限制每个 IP 的请求频率</li>
 * </ul>
 *
 * <p>Lua 脚本位于 resources/lua/ 目录下。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;

    // 预加载的 Lua 脚本
    private DefaultRedisScript<List> fixedWindowScript;
    private DefaultRedisScript<List> slidingWindowScript;
    private DefaultRedisScript<List> tokenBucketScript;

    /**
     * 初始化：从 classpath 加载 Lua 脚本
     */
    @jakarta.annotation.PostConstruct
    public void initScripts() throws IOException {
        fixedWindowScript = loadScript("lua/rate_limit_fixed_window.lua");
        slidingWindowScript = loadScript("lua/rate_limit_sliding_window.lua");
        tokenBucketScript = loadScript("lua/rate_limit_token_bucket.lua");
        log.info("Rate limit Lua scripts loaded successfully");
    }

    private DefaultRedisScript<List> loadScript(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        byte[] bytes = resource.getContentAsByteArray();
        String scriptText = new String(bytes, StandardCharsets.UTF_8);

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(List.class);
        return script;
    }

    /**
     * 限流结果
     *
     * @param allowed   是否允许通过
     * @param limit     限制数量
     * @param remaining 剩余可用数量
     * @param resetMs   重置时间（毫秒时间戳）
     */
    public record RateLimitResult(
            boolean allowed,
            long limit,
            long remaining,
            long resetMs
    ) {}

    /**
     * 固定窗口限流
     *
     * <p>在时间窗口内限制请求数量，窗口结束后重置。
     *
     * @param key     限流键
     * @param limit   限制数量
     * @param windowMs 窗口大小（毫秒）
     * @return 限流结果
     */
    public RateLimitResult fixedWindow(String key, long limit, long windowMs) {
        String redisKey = "rate_limit:fixed:" + key;

        long now = System.currentTimeMillis();
        List<Long> result = redisTemplate.execute(fixedWindowScript,
                Collections.singletonList(redisKey),
                String.valueOf(limit),
                String.valueOf(windowMs),
                String.valueOf(now));

        boolean allowed = result.get(0) == 1;
        long remaining = result.get(2);
        long resetMs = result.get(3);

        if (!allowed) {
            log.warn("Rate limit exceeded: key={}, limit={}", key, limit);
        }

        return new RateLimitResult(allowed, limit, remaining, resetMs);
    }

    /**
     * 滑动窗口限流（基于 Redis ZSET）
     *
     * <p>更精确的限流算法，记录每个请求的时间戳。
     *
     * @param key     限流键
     * @param limit   限制数量
     * @param windowMs 窗口大小（毫秒）
     * @return 限流结果
     */
    public RateLimitResult slidingWindow(String key, long limit, long windowMs) {
        String redisKey = "rate_limit:sliding:" + key;
        long now = System.currentTimeMillis();

        List<Long> result = redisTemplate.execute(slidingWindowScript,
                Collections.singletonList(redisKey),
                String.valueOf(limit),
                String.valueOf(windowMs),
                String.valueOf(now));

        boolean allowed = result.get(0) == 1;
        long remaining = result.get(2);
        long resetMs = result.get(3);

        if (!allowed) {
            log.warn("Rate limit exceeded (sliding): key={}, limit={}", key, limit);
        }

        return new RateLimitResult(allowed, limit, remaining, resetMs);
    }

    /**
     * 令牌桶限流
     *
     * <p>基于令牌桶算法，支持突发流量。
     *
     * @param key         限流键
     * @param capacity    桶容量
     * @param ratePerSec  每秒生成令牌数
     * @return 限流结果
     */
    public RateLimitResult tokenBucket(String key, long capacity, double ratePerSec) {
        String redisKey = "rate_limit:token:" + key;
        long now = System.currentTimeMillis();

        List<Long> result = redisTemplate.execute(tokenBucketScript,
                Collections.singletonList(redisKey),
                String.valueOf(capacity),
                String.valueOf(ratePerSec),
                String.valueOf(now));

        boolean allowed = result.get(0) == 1;
        long remaining = result.get(2);
        long resetMs = result.get(3);

        if (!allowed) {
            log.warn("Rate limit exceeded (token bucket): key={}, capacity={}", key, capacity);
        }

        return new RateLimitResult(allowed, capacity, remaining, resetMs);
    }

    /**
     * 检查 API Key 限流
     *
     * @param apiKeyId API Key ID
     * @param limit    限制配置（格式：100/min, 1000/hour, 10000/day）
     * @return 限流结果
     */
    public RateLimitResult checkApiKeyLimit(Long apiKeyId, String limit) {
        RateLimitConfig config = parseLimit(limit);
        String key = "api_key:" + apiKeyId;
        return tokenBucket(key, config.maxRequests(), config.ratePerSecond());
    }

    /**
     * 检查用户限流
     *
     * @param userId 用户ID
     * @param limit  限制配置
     * @return 限流结果
     */
    public RateLimitResult checkUserLimit(Long userId, String limit) {
        RateLimitConfig config = parseLimit(limit);
        String key = "user:" + userId;
        return tokenBucket(key, config.maxRequests(), config.ratePerSecond());
    }

    /**
     * 检查 IP 限流
     *
     * @param ip    IP 地址
     * @param limit 限制配置
     * @return 限流结果
     */
    public RateLimitResult checkIpLimit(String ip, String limit) {
        RateLimitConfig config = parseLimit(limit);
        String key = "ip:" + ip;
        return tokenBucket(key, config.maxRequests(), config.ratePerSecond());
    }

    /**
     * 解析限制配置
     *
     * <p>格式：100/min, 1000/hour, 10000/day
     *
     * @param limit 限制配置字符串
     * @return 解析后的配置
     */
    private RateLimitConfig parseLimit(String limit) {
        String[] parts = limit.split("/");
        long maxRequests = Long.parseLong(parts[0]);
        String period = parts[1].toLowerCase();

        double ratePerSecond;
        switch (period) {
            case "sec":
            case "second":
                ratePerSecond = maxRequests;
                break;
            case "min":
            case "minute":
                ratePerSecond = maxRequests / 60.0;
                break;
            case "hour":
                ratePerSecond = maxRequests / 3600.0;
                break;
            case "day":
                ratePerSecond = maxRequests / 86400.0;
                break;
            default:
                throw new IllegalArgumentException("Invalid period: " + period);
        }

        return new RateLimitConfig(maxRequests, ratePerSecond);
    }

    /**
     * 限流配置
     *
     * @param maxRequests  最大请求数
     * @param ratePerSecond 每秒速率
     */
    private record RateLimitConfig(long maxRequests, double ratePerSecond) {}
}
