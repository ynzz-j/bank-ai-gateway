package com.bank.ai.gateway.security;

/**
 * 审计上下文 - 存储当前操作用户信息
 *
 * <p>通过 ThreadLocal 存储，在 Filter 中设置。
 *
 * @since 1.0.0
 */
public class AuditContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    public static Long getUserId() {
        Long id = USER_ID.get();
        return id != null ? id : 0L;
    }

    public static String getUsername() {
        String name = USERNAME.get();
        return name != null ? name : "system";
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
    }
}