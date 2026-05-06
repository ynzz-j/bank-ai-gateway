package com.bank.ai.gateway.model.dto.request.channel;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 渠道更新请求
 *
 * @since 1.0.0
 */
@Data
public class ChannelUpdateRequest {

    @Size(max = 100, message = "渠道名称最长100字符")
    private String name;

    @Size(max = 500, message = "API Key最长500字符")
    private String apiKey;

    private String baseUrl;

    private String models;

    private Integer priority;

    private Integer weight;

    /**
     * 状态：1启用 2熔断 3不可用
     */
    private Short status;
}