package com.bank.ai.gateway.model.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审计日志查询请求
 *
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogQueryRequest extends DateRangeRequest {

    /**
     * 操作类型：CREATE/UPDATE/DELETE/ENABLE/DISABLE/ROTATE
     */
    private String action;

    /**
     * 资源类型：channel/api_key/rate_limit_config等
     */
    private String resourceType;

    /**
     * 操作用户ID
     */
    private Long userId;
}