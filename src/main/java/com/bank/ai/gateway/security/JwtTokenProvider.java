package com.bank.ai.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT Token 提供者
 *
 * <p>负责 JWT Token 的生成、解析和校验。
 *
 * @since 1.0.0
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final Duration accessTokenValidity;
    private final Duration refreshTokenValidity;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity}") String accessValidity,
            @Value("${jwt.refresh-token-validity}") String refreshValidity) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = parseDuration(accessValidity);
        this.refreshTokenValidity = parseDuration(refreshValidity);
    }

    /**
     * 生成访问 Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param role     角色
     * @return JWT Token
     */
    public String generateAccessToken(Long userId, String username, String role) {
        return generateToken(userId, username, role, accessTokenValidity, "access");
    }

    /**
     * 生成刷新 Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param role     角色
     * @return JWT Token
     */
    public String generateRefreshToken(Long userId, String username, String role) {
        return generateToken(userId, username, role, refreshTokenValidity, "refresh");
    }

    /**
     * 解析 Token 获取用户ID
     *
     * @param token JWT Token
     * @return 用户ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("userId", Long.class) : null;
    }

    /**
     * 解析 Token 获取用户名
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * 解析 Token 获取角色
     *
     * @param token JWT Token
     * @return 角色
     */
    public String getRole(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("role", String.class) : null;
    }

    /**
     * 校验 Token 是否有效
     *
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT Token 已过期: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("JWT Token 无效: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 检查 Token 是否过期
     *
     * @param token JWT Token
     * @return 是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            if (claims == null) {
                return true;
            }
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 获取访问 Token 有效期（秒）
     *
     * @return 有效期秒数
     */
    public long getAccessTokenValiditySeconds() {
        return accessTokenValidity.toSeconds();
    }

    /**
     * 获取刷新 Token 有效期（秒）
     *
     * @return 有效期秒数
     */
    public long getRefreshTokenValiditySeconds() {
        return refreshTokenValidity.toSeconds();
    }

    // ==================== 私有方法 ====================

    private String generateToken(Long userId, String username, String role, Duration validity, String type) {
        Instant now = Instant.now();
        Instant expiration = now.plus(validity);

        return Jwts.builder()
                .subject(username)
                .claims(Map.of(
                        "userId", userId,
                        "role", role,
                        "type", type
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    private Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("解析 Token 失败: {}", e.getMessage());
            return null;
        }
    }

    private Duration parseDuration(String value) {
        if (value.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(value.substring(0, value.length() - 1)));
        } else if (value.endsWith("d")) {
            return Duration.ofDays(Long.parseLong(value.substring(0, value.length() - 1)));
        } else if (value.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1)));
        } else if (value.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
        } else {
            return Duration.ofSeconds(Long.parseLong(value));
        }
    }
}
