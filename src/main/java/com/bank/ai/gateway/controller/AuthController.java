package com.bank.ai.gateway.controller;

import com.bank.ai.gateway.common.ApiResponse;
import com.bank.ai.gateway.model.dto.request.LoginRequest;
import com.bank.ai.gateway.model.dto.response.LoginResponse;
import com.bank.ai.gateway.model.dto.response.UserInfoResponse;
import com.bank.ai.gateway.security.JwtTokenProvider;
import com.bank.ai.gateway.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 认证控制器
 *
 * <p>提供登录、刷新 Token、获取当前用户等接口。
 *
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应（含 Token）
     */
    @PostMapping("/login")
    public Mono<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("用户登录请求: {}", request.getUsername());
        return Mono.fromCallable(() -> authService.login(request))
                .map(ApiResponse::success);
    }

    /**
     * 刷新 Token
     *
     * @param refreshToken 刷新 Token
     * @return 新的登录响应
     */
    @PostMapping("/refresh")
    public Mono<ApiResponse<LoginResponse>> refreshToken(
            @RequestHeader("Refresh-Token") String refreshToken) {
        log.debug("刷新 Token 请求");
        return Mono.fromCallable(() -> authService.refreshToken(refreshToken))
                .map(ApiResponse::success);
    }

    /**
     * 获取当前用户信息
     *
     * @param exchange ServerWebExchange
     * @return 用户信息
     */
    @GetMapping("/me")
    public Mono<ApiResponse<UserInfoResponse>> getCurrentUser(ServerWebExchange exchange) {
        return Mono.deferContextual(contextView -> {
            Long userId = contextView.getOrDefault("userId", null);
            if (userId == null) {
                return Mono.just(ApiResponse.error(10001, "未认证"));
            }
            return Mono.fromCallable(() -> authService.getCurrentUser(userId))
                    .map(ApiResponse::success);
        });
    }

    /**
     * 登出（客户端清除 Token 即可，服务端无需处理）
     *
     * @return 成功响应
     */
    @PostMapping("/logout")
    public Mono<ApiResponse<Void>> logout() {
        return Mono.just(ApiResponse.success());
    }
}
