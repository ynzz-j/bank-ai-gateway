package com.bank.ai.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PasswordPolicy 单元测试
 *
 * @since 1.0.0
 */
class PasswordPolicyTest {

    private PasswordPolicy passwordPolicy;

    @BeforeEach
    void setUp() {
        passwordPolicy = new PasswordPolicy();
        ReflectionTestUtils.setField(passwordPolicy, "minLength", 8);
        ReflectionTestUtils.setField(passwordPolicy, "requireUpper", true);
        ReflectionTestUtils.setField(passwordPolicy, "requireLower", true);
        ReflectionTestUtils.setField(passwordPolicy, "requireDigit", true);
        ReflectionTestUtils.setField(passwordPolicy, "requireSpecial", true);
    }

    @Test
    void testValidate_ValidPassword() {
        String password = "Test@123456";
        PasswordPolicy.PasswordValidationResult result = passwordPolicy.validate(password);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testValidate_TooShort() {
        String password = "Test@12";
        PasswordPolicy.PasswordValidationResult result = passwordPolicy.validate(password);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("密码长度不能少于"));
    }

    @Test
    void testValidate_NoUpperCase() {
        String password = "test@123456";
        PasswordPolicy.PasswordValidationResult result = passwordPolicy.validate(password);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("大写字母"));
    }

    @Test
    void testValidate_NoLowerCase() {
        String password = "TEST@123456";
        PasswordPolicy.PasswordValidationResult result = passwordPolicy.validate(password);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("小写字母"));
    }

    @Test
    void testValidate_NoDigit() {
        String password = "Test@password";
        PasswordPolicy.PasswordValidationResult result = passwordPolicy.validate(password);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("数字"));
    }

    @Test
    void testValidate_NoSpecialChar() {
        String password = "Test123456";
        PasswordPolicy.PasswordValidationResult result = passwordPolicy.validate(password);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("特殊字符"));
    }

    @Test
    void testValidate_Empty() {
        String password = "";
        PasswordPolicy.PasswordValidationResult result = passwordPolicy.validate(password);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("密码不能为空"));
    }

    @Test
    void testValidate_Null() {
        PasswordPolicy.PasswordValidationResult result = passwordPolicy.validate(null);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("密码不能为空"));
    }

    @Test
    void testValidate_MultipleErrors() {
        String password = "test"; // 短、无大写、无数字、无特殊字符
        PasswordPolicy.PasswordValidationResult result = passwordPolicy.validate(password);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().size() >= 3);
    }
}
