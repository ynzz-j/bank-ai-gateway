package com.bank.ai.gateway.security;

import com.bank.ai.gateway.common.ApiResponse;
import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 认证过滤器
 *
 * <p>负责 JWT Token 校验和 API Key 认证。
 *
 * <p>认证双轨制：
 * <ul>
 *   <li>管理端接口（/api/v1/auth/login 除外）：JWT Token 认证</li>
 *   <li>调用端接口（/api/v1/chat/*, /api/v1/models 等）：API Key 认证（后续实现）</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
public class AuthFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    /**
     * 白名单路径（无需认证）
     */
    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/login",
            "/actuator/health",
            "/actuator/info"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 1. 白名单路径直接放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 2. 获取 Authorization Header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            return unauthorized(exchange, "缺少认证信息");
        }

        // 3. 根据认证类型处理
        if (authHeader.startsWith("Bearer ")) {
            return handleJwtAuth(exchange, chain, authHeader.substring(7));
        } else if (authHeader.startsWith("sk-")) {
            // API Key 认证（后续实现）
            return handleApiKeyAuth(exchange, chain, authHeader);
        } else {
            return unauthorized(exchange, "不支持的认证方式");
        }
    }

    /**
     * 处理 JWT 认证
     */
    private Mono<Void> handleJwtAuth(ServerWebExchange exchange, WebFilterChain chain, String token) {
        // 校验 Token
        if (!jwtTokenProvider.validateToken(token)) {
            return unauthorized(exchange, "Token 无效或已过期");
        }

        // 解析用户信息
        Long userId = jwtTokenProvider.getUserId(token);
        String username = jwtTokenProvider.getUsername(token);
        String role = jwtTokenProvider.getRole(token);

        if (userId == null || username == null) {
            return unauthorized(exchange, "Token 解析失败");
        }

        log.debug("JWT 认证成功: userId={}, username={}, role={}", userId, username, role);

        // 将用户信息写入 Context
        return chain.filter(exchange)
                .contextWrite(Context.of(
                        "userId", userId,
                        "username", username,
                        "role", role
                ));
    }

    /**
     * 处理 API Key 认证（占位，后续实现）
     */
    private Mono<Void> handleApiKeyAuth(ServerWebExchange exchange, WebFilterChain chain, String apiKey) {
        // TODO: 实现 API Key 认证
        log.debug("API Key 认证暂未实现: {}", apiKey.substring(0, Math.min(8, apiKey.length())) + "***");
        return unauthorized(exchange, "API Key 认证暂未实现");
    }

    /**
     * 检查路径是否在白名单中
     */
    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    /**
     * 返回 401 未认证响应
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> response = ApiResponse.error(ErrorCode.AUTH_FAILED.getCode(), message);
        String body;
        try {
            body = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            body = "{\"code\":10001,\"message\":\"认证失败\"}";
        }

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8)))
        );
    }
}
