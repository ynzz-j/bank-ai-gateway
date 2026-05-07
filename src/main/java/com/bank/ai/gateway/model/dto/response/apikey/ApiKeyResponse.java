package com.bank.ai.gateway.model.dto.response.apikey;

import lombok.Data;

/**
 * API Key 响应（不包含完整Key）
 *
 * @since 1.0.0
 */
@Data
public class ApiKeyResponse {

    /** 主键ID */
    private Long id;

    /** Key前缀（明文），用于快速定位 */
    private String keyPrefix;

    /** Key名称 */
    private String name;

    /** 每分钟请求数配额，-1表示无限制 */
    private Integer quotaRpm;

    /** 每分钟Token数配额，-1表示无限制 */
    private Integer quotaTpm;

    /** 总配额(Token数)，-1表示无限制 */
    private Long quotaTotal;

    /** 已用配额 */
    private Long usedQuota;

    /** 状态: 1启用 0禁用 2已轮换 */
    private Short status;

    /** 过期时间 */
    private String expiresAt;

    /** 创建时间 */
    private String createdAt;

    /** 状态描述 */
    private String statusDesc;
}