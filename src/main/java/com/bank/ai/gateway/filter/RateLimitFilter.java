package com.bank.ai.gateway.filter;

import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.model.entity.apikey.ApiKeyEntity;
import com.bank.ai.gateway.repository.apikey.ApiKeyMapper;
import com.bank.ai.gateway.service.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 限流过滤器（WebFlux）
 *
 * <p>拦截请求，根据 API Key、IP 维度进行限流检查。
 *
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements WebFilter {

    private final RateLimitService rateLimitService;
    private final ApiKeyMapper apiKeyMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 只拦截聊天接口
        if (!path.contains("/chat")) {
            return chain.filter(exchange);
        }

        String clientIp = getClientIp(exchange);

        // 1. IP 限流
        return checkIpRateLimit(clientIp)
                .flatMap(ipAllowed -> {
                    if (!ipAllowed) {
                        return buildRateLimitResponse(exchange, "IP 限流超限，请稍后重试");
                    }

                    // 2. API Key 限流
                    String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String apiKeyPrefix = authHeader.substring(7, Math.min(17, authHeader.length()));
                        return checkApiKeyRateLimit(apiKeyPrefix)
                                .flatMap(keyAllowed -> {
                                    if (!keyAllowed) {
                                        return buildRateLimitResponse(exchange, "API Key 限流超限，请稍后重试");
                                    }
                                    return chain.filter(exchange);
                                });
                    }

                    return chain.filter(exchange);
                });
    }

    /**
     * 检查 IP 限流
     */
    private Mono<Boolean> checkIpRateLimit(String ip) {
        // 默认配置：每分钟 60 次
        String limitConfig = "60/min";

        return Mono.fromCallable(() -> {
            RateLimitService.RateLimitResult result = rateLimitService.checkIpLimit(ip, limitConfig);
            boolean allowed = result.allowed();
            if (!allowed) {
                log.warn("IP rate limit exceeded: ip={}", ip);
            }
            return allowed;
        });
    }

    /**
     * 检查 API Key 限流
     */
    private Mono<Boolean> checkApiKeyRateLimit(String apiKeyPrefix) {
        return Mono.fromCallable(() -> {
            // selectByPrefix 返回 List
            List<ApiKeyEntity> keys = apiKeyMapper.selectByPrefix(apiKeyPrefix);
            if (keys == null || keys.isEmpty()) {
                return true; // 找不到 API Key，跳过限流
            }
            ApiKeyEntity apiKey = keys.get(0);

            // 使用 quotaRpm（每分钟请求数）
            Integer quotaRpm = apiKey.getQuotaRpm();
            if (quotaRpm == null || quotaRpm <= 0) {
                return true; // 无限制
            }

            // 转换为令牌桶参数：容量=quotaRpm，速率=quotaRpm/60.0 每秒
            String key = "api_key:" + apiKey.getId();
            RateLimitService.RateLimitResult result = rateLimitService.tokenBucket(
                    key, quotaRpm, quotaRpm / 60.0);

            boolean allowed = result.allowed();
            if (!allowed) {
                log.warn("API Key rate limit exceeded: prefix={}", apiKeyPrefix);
            }
            return allowed;
        });
    }

    /**
     * 构建限流响应
     */
    private Mono<Void> buildRateLimitResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("""
                {"code": %d, "message": "%s"}
                """, ErrorCode.RATE_LIMIT_EXCEEDED.getCode(), message);

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().getHeaders().setContentLength(bytes.length);

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
        );
    }

    /**
     * 获取客户端 IP
     *
     * <p>支持反向代理场景（X-Forwarded-For、X-Real-IP）。
     */
    private String getClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }

        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }

        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}
