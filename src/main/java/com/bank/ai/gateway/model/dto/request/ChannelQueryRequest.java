package com.bank.ai.gateway.model.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 渠道查询请求
 *
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChannelQueryRequest extends PageRequest {

    /**
     * 服务商标识
     */
    private String provider;

    /**
     * 状态：true启用 false禁用
     */
    private Boolean enabled;
}