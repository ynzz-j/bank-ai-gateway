package com.bank.ai.gateway.model.dto.request.apikey;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新 API Key 请求
 *
 * @since 1.0.0
 */
@Data
public class UpdateApiKeyRequest {

    /** Key名称 */
    @Size(max = 64, message = "名称最多64字符")
    private String name;

    /** 每分钟请求数配额，-1表示无限制 */
    @Min(value = -1, message = "配额不能小于-1")
    private Integer quotaRpm;

    /** 每分钟Token数配额，-1表示无限制 */
    @Min(value = -1, message = "配额不能小于-1")
    private Integer quotaTpm;

    /** 总配额(Token数)，-1表示无限制 */
    @Min(value = -1, message = "配额不能小于-1")
    private Long quotaTotal;

    /** 状态: 1启用 0禁用 */
    @Min(value = 0) @Max(value = 1)
    private Integer status;

    /** 过期时间，格式: yyyy-MM-dd HH:mm:ss */
    private String expiresAt;
}