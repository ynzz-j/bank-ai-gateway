package com.bank.ai.gateway.service;

import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.model.dto.request.LoginRequest;
import com.bank.ai.gateway.model.dto.response.LoginResponse;
import com.bank.ai.gateway.model.dto.response.UserInfoResponse;
import com.bank.ai.gateway.model.entity.User;
import com.bank.ai.gateway.repository.UserMapper;
import com.bank.ai.gateway.security.JwtTokenProvider;
import com.bank.ai.gateway.security.PasswordEncoder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务
 *
 * <p>负责用户登录、Token 生成、登录失败锁定等功能。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${password.max-login-fail:5}")
    private int maxLoginFail;

    @Value("${password.lock-duration:30m}")
    private String lockDuration;

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应（含 Token）
     * @throws BizException 认证失败、账号锁定等
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. 查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );

        if (user == null) {
            log.warn("登录失败，用户不存在: {}", request.getUsername());
            throw new BizException(ErrorCode.AUTH_FAILED);
        }

        // 2. 检查账号状态
        if (user.getStatus() == 0) {
            log.warn("登录失败，账号已禁用: {}", request.getUsername());
            throw new BizException(ErrorCode.ACCOUNT_LOCKED, "账号已禁用");
        }

        // 3. 检查是否锁定
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            log.warn("登录失败，账号已锁定至: {}", user.getLockedUntil());
            throw new BizException(ErrorCode.ACCOUNT_LOCKED, "账号已锁定，请稍后重试");
        }

        // 4. 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // 密码错误，增加失败次数
            handleLoginFail(user);
            throw new BizException(ErrorCode.AUTH_FAILED);
        }

        // 5. 登录成功，重置失败次数，更新最后登录时间
        user.setLoginFailCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户登录成功: {}", user.getUsername());

        // 6. 生成 Token
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());

        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenValiditySeconds())
                .build();
    }

    /**
     * 获取当前用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    public UserInfoResponse getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.AUTH_FAILED, "用户不存在");
        }

        return UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }

    /**
     * 刷新 Token
     *
     * @param refreshToken 刷新 Token
     * @return 新的登录响应
     */
    public LoginResponse refreshToken(String refreshToken) {
        // 校验 refreshToken
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BizException(ErrorCode.JWT_TOKEN_EXPIRED);
        }

        String type = getTypeFromToken(refreshToken);
        if (!"refresh".equals(type)) {
            throw new BizException(ErrorCode.AUTH_FAILED, "无效的刷新 Token");
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String username = jwtTokenProvider.getUsername(refreshToken);
        String role = jwtTokenProvider.getRole(refreshToken);

        // 生成新的 Token
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, username, role);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, username, role);

        return LoginResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenValiditySeconds())
                .build();
    }

    // ==================== 私有方法 ====================

    /**
     * 处理登录失败
     */
    private void handleLoginFail(User user) {
        int failCount = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;
        user.setLoginFailCount(failCount);
        user.setUpdatedAt(LocalDateTime.now());

        if (failCount >= maxLoginFail) {
            // 锁定账号
            LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(parseLockDuration());
            user.setLockedUntil(lockedUntil);
            log.warn("账号已锁定，失败次数: {}, 锁定至: {}", failCount, lockedUntil);
        }

        userMapper.updateById(user);
    }

    /**
     * 解析锁定时长
     */
    private long parseLockDuration() {
        String value = lockDuration.trim();
        if (value.endsWith("m")) {
            return Long.parseLong(value.substring(0, value.length() - 1));
        } else if (value.endsWith("h")) {
            return Long.parseLong(value.substring(0, value.length() - 1)) * 60;
        } else {
            return 30; // 默认 30 分钟
        }
    }

    /**
     * 从 Token 获取类型
     */
    private String getTypeFromToken(String token) {
        try {
            io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.parser()
                    .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                            jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("type", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Value("${jwt.secret}")
    private String jwtSecret;
}
