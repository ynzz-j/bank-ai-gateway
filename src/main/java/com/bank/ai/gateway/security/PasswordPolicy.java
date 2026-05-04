package com.bank.ai.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 密码策略校验器
 *
 * <p>校验密码强度：
 * <ul>
 *   <li>最小长度：8 位</li>
 *   <li>必须包含大写字母</li>
 *   <li>必须包含小写字母</li>
 *   <li>必须包含数字</li>
 *   <li>必须包含特殊字符</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@Component
public class PasswordPolicy {

    @Value("${password.min-length:8}")
    private int minLength;

    @Value("${password.require-upper:true}")
    private boolean requireUpper;

    @Value("${password.require-lower:true}")
    private boolean requireLower;

    @Value("${password.require-digit:true}")
    private boolean requireDigit;

    @Value("${password.require-special:true}")
    private boolean requireSpecial;

    private static final String SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

    /**
     * 校验密码强度
     *
     * @param password 原始密码
     * @return 校验结果
     */
    public PasswordValidationResult validate(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errors.add("密码不能为空");
            return new PasswordValidationResult(false, errors);
        }

        // 长度校验
        if (password.length() < minLength) {
            errors.add("密码长度不能少于 " + minLength + " 位");
        }

        // 大写字母
        if (requireUpper && !password.matches(".*[A-Z].*")) {
            errors.add("密码必须包含大写字母");
        }

        // 小写字母
        if (requireLower && !password.matches(".*[a-z].*")) {
            errors.add("密码必须包含小写字母");
        }

        // 数字
        if (requireDigit && !password.matches(".*\\d.*")) {
            errors.add("密码必须包含数字");
        }

        // 特殊字符
        if (requireSpecial && !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?].*")) {
            errors.add("密码必须包含特殊字符");
        }

        boolean valid = errors.isEmpty();
        return new PasswordValidationResult(valid, errors);
    }

    /**
     * 密码校验结果
     */
    public static class PasswordValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public PasswordValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            return String.join("; ", errors);
        }
    }
}
