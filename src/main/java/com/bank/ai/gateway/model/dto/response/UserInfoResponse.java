package com.bank.ai.gateway.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息响应
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    /** 用户ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 角色 */
    private String role;

    /** 状态：1启用 0禁用 */
    private Integer status;
}
