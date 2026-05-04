package com.bank.ai.gateway.model.dto.request.apikey;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 API Key 请求
 *
 * @since 1.0.0
 */
@Data
public class CreateApiKeyRequest {

    /** Key名称 */
    @Size(max = 64, message = "名称最多64字符")
    private String name;

    /** 每分钟请求数配额，-1表示无限制，默认60 */
    @Min(value = -1, message = "配额不能小于-1")
    private Integer quotaRpm = 60;

    /** 每分钟Token数配额，-1表示无限制，默认100000 */
    @Min(value = -1, message = "配额不能小于-1")
    private Integer quotaTpm = 100000;

    /** 总配额(Token数)，-1表示无限制 */
    @Min(value = -1, message = "配额不能小于-1")
    private Long quotaTotal = -1L;

    /** 过期时间，null表示永不过期，格式: yyyy-MM-dd HH:mm:ss */
    private String expiresAt;
}