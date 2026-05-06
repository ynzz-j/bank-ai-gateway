package com.bank.ai.gateway.common;

import lombok.Getter;

/**
 * 错误码枚举
 *
 * <p>错误码结构：{模块}{分类}{序号}
 * <ul>
 *   <li>1xxxx：认证相关</li>
 *   <li>2xxxx：路由相关</li>
 *   <li>3xxxx：合规相关</li>
 *   <li>4xxxx：限流相关</li>
 *   <li>5xxxx：系统相关</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Getter
public enum ErrorCode {

    // ==================== 1xxxx 认证相关 ====================
    UNAUTHORIZED(10000, "未授权"),
    AUTH_FAILED(10001, "认证失败"),
    API_KEY_INVALID(10002, "API Key 无效"),
    API_KEY_EXPIRED(10003, "API Key 已过期"),
    API_KEY_QUOTA_EXCEEDED(10004, "API Key 配额已用尽"),
    ACCOUNT_LOCKED(10005, "账号已锁定"),
    JWT_TOKEN_EXPIRED(10006, "JWT Token 过期"),
    PERMISSION_DENIED(10007, "权限不足"),

    // ==================== 2xxxx 路由相关 ====================
    MODEL_NOT_FOUND(20001, "模型不存在"),
    NO_AVAILABLE_CHANNEL(20002, "无可用渠道"),
    CHANNEL_UNAVAILABLE(20003, "渠道不可用"),
    CHANNEL_ERROR(20004, "渠道调用失败"),
    UPSTREAM_TIMEOUT(20005, "上游服务超时"),
    UPSTREAM_ERROR(20006, "上游服务错误"),

    // ==================== 3xxxx 合规相关 ====================
    CONTENT_BLOCKED(30001, "内容被拦截"),
    SENSITIVE_WORD_DETECTED(30002, "检测到敏感词"),
    PRIVACY_LEAK_RISK(30003, "隐私信息泄露风险"),

    // ==================== 4xxxx 限流相关 ====================
    RATE_LIMIT_EXCEEDED(40001, "请求频率超限"),
    GLOBAL_RATE_LIMIT(40002, "全局限流触发"),
    MODEL_RATE_LIMIT(40003, "模型限流触发"),

    // ==================== 5xxxx 系统相关 ====================
    NOT_FOUND(50000, "资源不存在"),
    BAD_REQUEST(50001, "请求参数错误"),
    SYSTEM_ERROR(50002, "系统内部错误"),
    INTERNAL_ERROR(50003, "系统内部错误"),
    PARAM_INVALID(50004, "参数校验失败"),
    DATABASE_ERROR(50005, "数据库错误"),
    REDIS_ERROR(50006, "Redis 错误"),

    // ==================== 6xxxx API Key管理 ====================
    API_KEY_NOT_FOUND(60001, "API Key 不存在"),
    API_KEY_DISABLED(60002, "API Key 已禁用"),
    API_KEY_EXPIRED_DUPLICATE(60003, "API Key 已过期"),
    API_KEY_REVOKED(60004, "API Key 已撤销"),
    API_KEY_QUOTA_NOT_ENOUGH(60005, "配额不足"),
    API_KEY_NAME_DUPLICATE(60006, "Key名称已存在"),

    // ==================== 7xxxx 合规相关 ====================
    COMPLIANCE_BLOCKED(70001, "内容违规被拦截"),
    COMPLIANCE_WARNING(70002, "内容存在风险");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}