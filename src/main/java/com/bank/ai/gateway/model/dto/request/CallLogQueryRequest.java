package com.bank.ai.gateway.model.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 调用日志查询请求
 *
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CallLogQueryRequest extends DateRangeRequest {

    /**
     * API Key ID
     */
    private Long apiKeyId;

    /**
     * 服务商标识
     */
    private String provider;

    /**
     * 模型名称
     */
    private String model;
}