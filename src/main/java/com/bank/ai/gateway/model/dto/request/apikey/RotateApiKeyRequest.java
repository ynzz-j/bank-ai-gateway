package com.bank.ai.gateway.model.dto.request.apikey;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 轮换 API Key 请求
 *
 * @since 1.0.0
 */
@Data
public class RotateApiKeyRequest {

    /** 新Key每分钟请求数配额，-1表示无限制，默认继承原值 */
    @Min(value = -1, message = "配额不能小于-1")
    private Integer quotaRpm;

    /** 新Key每分钟Token数配额，-1表示无限制，默认继承原值 */
    @Min(value = -1, message = "配额不能小于-1")
    private Integer quotaTpm;

    /** 新Key总配额(Token数)，-1表示无限制，默认继承原值 */
    @Min(value = -1, message = "配额不能小于-1")
    private Long quotaTotal;
}