package com.bank.ai.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtTokenProvider 单元测试
 *
 * @since 1.0.0
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "changeme-this-must-be-at-least-256-bits-long-for-hs256-algorithm",
                "2h",
                "7d"
        );
    }

    @Test
    void testGenerateAccessToken() {
        String token = jwtTokenProvider.generateAccessToken(1L, "admin", "ADMIN");

        assertNotNull(token);
        assertTrue(token.length() > 0);
        assertTrue(token.split("\\.").length == 3); // JWT 格式: header.payload.signature
    }

    @Test
    void testGenerateRefreshToken() {
        String token = jwtTokenProvider.generateRefreshToken(1L, "admin", "ADMIN");

        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    @Test
    void testGetUserId() {
        String token = jwtTokenProvider.generateAccessToken(1L, "admin", "ADMIN");
        Long userId = jwtTokenProvider.getUserId(token);

        assertEquals(1L, userId);
    }

    @Test
    void testGetUsername() {
        String token = jwtTokenProvider.generateAccessToken(1L, "admin", "ADMIN");
        String username = jwtTokenProvider.getUsername(token);

        assertEquals("admin", username);
    }

    @Test
    void testGetRole() {
        String token = jwtTokenProvider.generateAccessToken(1L, "admin", "ADMIN");
        String role = jwtTokenProvider.getRole(token);

        assertEquals("ADMIN", role);
    }

    @Test
    void testValidateToken_Valid() {
        String token = jwtTokenProvider.generateAccessToken(1L, "admin", "ADMIN");
        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void testValidateToken_Invalid() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.here"));
    }

    @Test
    void testValidateToken_Empty() {
        assertFalse(jwtTokenProvider.validateToken(""));
    }

    @Test
    void testIsTokenExpired_NotExpired() {
        String token = jwtTokenProvider.generateAccessToken(1L, "admin", "ADMIN");
        assertFalse(jwtTokenProvider.isTokenExpired(token));
    }

    @Test
    void testGetAccessTokenValiditySeconds() {
        long seconds = jwtTokenProvider.getAccessTokenValiditySeconds();
        assertEquals(2 * 60 * 60, seconds); // 2h = 7200s
    }

    @Test
    void testGetRefreshTokenValiditySeconds() {
        long seconds = jwtTokenProvider.getRefreshTokenValiditySeconds();
        assertEquals(7 * 24 * 60 * 60, seconds); // 7d = 604800s
    }
}
