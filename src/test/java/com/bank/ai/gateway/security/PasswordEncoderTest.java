package com.bank.ai.gateway.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PasswordEncoder 单元测试
 *
 * @since 1.0.0
 */
class PasswordEncoderTest {

    private final PasswordEncoder passwordEncoder = new PasswordEncoder();

    @Test
    void testEncode() {
        String rawPassword = "Test@123456";
        String encoded = passwordEncoder.encode(rawPassword);

        assertNotNull(encoded);
        assertTrue(encoded.startsWith("$2a$")); // bcrypt 格式
        assertNotEquals(rawPassword, encoded);
    }

    @Test
    void testMatches_Success() {
        String rawPassword = "Test@123456";
        String encoded = passwordEncoder.encode(rawPassword);

        assertTrue(passwordEncoder.matches(rawPassword, encoded));
    }

    @Test
    void testMatches_Fail() {
        String rawPassword = "Test@123456";
        String wrongPassword = "Wrong@123456";
        String encoded = passwordEncoder.encode(rawPassword);

        assertFalse(passwordEncoder.matches(wrongPassword, encoded));
    }

    @Test
    void testEncode_DifferentEachTime() {
        String rawPassword = "Test@123456";
        String encoded1 = passwordEncoder.encode(rawPassword);
        String encoded2 = passwordEncoder.encode(rawPassword);

        // bcrypt 每次编码结果不同（盐值不同）
        assertNotEquals(encoded1, encoded2);
        // 但都能匹配原密码
        assertTrue(passwordEncoder.matches(rawPassword, encoded1));
        assertTrue(passwordEncoder.matches(rawPassword, encoded2));
    }
}
