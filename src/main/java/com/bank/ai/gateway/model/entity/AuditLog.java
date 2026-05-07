package com.bank.ai.gateway.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 *
 * <p>记录所有管理操作（增删改），只增不改不删。
 * 用于合规审计和操作追溯。
 *
 * @since 1.0.0
 */
@Data
@TableName("audit_logs")
public class AuditLog {

    /**
     * 操作类型枚举
     */
    public enum Action {
        CREATE("创建"),
        UPDATE("更新"),
        DELETE("删除"),
        DISABLE("禁用"),
        ENABLE("启用"),
        ROTATE("轮换"),
        LOGIN("登录"),
        LOGOUT("登出"),
        REFRESH_TOKEN("刷新令牌");

        private final String desc;

        Action(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    /**
     * 资源类型枚举
     */
    public enum ResourceType {
        API_KEY("API Key"),
        CHANNEL("渠道"),
        MODEL_MAPPING("模型映射"),
        CONFIG("系统配置"),
        SENSITIVE_WORD("敏感词"),
        USER("用户");

        private final String desc;

        ResourceType(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人ID */
    private Long userId;

    /** 操作人用户名 */
    private String username;

    /** 操作类型 */
    private String action;

    /** 资源类型 */
    private String resourceType;

    /** 资源ID */
    private Long resourceId;

    /** 资源名称 */
    private String resourceName;

    /** 变更快照（JSON） */
    private String changeSnapshot;

    /** 请求IP */
    private String requestIp;

    /** User-Agent */
    private String userAgent;

    /** 请求ID */
    private String requestId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}