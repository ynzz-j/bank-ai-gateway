package com.bank.ai.gateway.model.dto.response.apikey;

import lombok.Data;

/**
 * 创建 API Key 响应（仅创建时返回完整Key）
 *
 * <p>⚠️ 完整Key仅在此响应中返回一次，之后无法找回
 *
 * @since 1.0.0
 */
@Data
public class CreateApiKeyResponse {

    /** 主键ID */
    private Long id;

    /** 完整API Key（仅创建时返回） */
    private String apiKey;

    /** Key前缀 */
    private String keyPrefix;

    /** Key名称 */
    private String name;

    /** 每分钟请求数配额 */
    private Integer quotaRpm;

    /** 每分钟Token数配额 */
    private Integer quotaTpm;

    /** 总配额 */
    private Long quotaTotal;

    /** 过期时间 */
    private String expiresAt;

    /** 创建时间 */
    private String createdAt;

    /** 提示信息 */
    private String notice = "⚠️ 完整Key仅显示一次，请立即保存！";
}