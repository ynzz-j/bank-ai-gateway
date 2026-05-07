package com.bank.ai.gateway.model.dto.request.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 渠道创建请求
 *
 * @since 1.0.0
 */
@Data
public class ChannelCreateRequest {

    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 100, message = "渠道名称最长100字符")
    private String name;

    @NotBlank(message = "提供商不能为空")
    private String provider;

    @NotBlank(message = "API Key不能为空")
    @Size(max = 500, message = "API Key最长500字符")
    private String apiKey;

    @NotBlank(message = "Base URL不能为空")
    private String baseUrl;

    /**
     * 支持的模型列表（JSON数组格式）
     */
    private String models;

    @NotNull(message = "优先级不能为空")
    private Integer priority;

    @NotNull(message = "权重不能为空")
    private Integer weight;
}