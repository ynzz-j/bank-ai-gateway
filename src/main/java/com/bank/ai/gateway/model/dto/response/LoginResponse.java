package com.bank.ai.gateway.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** 访问 Token */
    private String token;

    /** 刷新 Token */
    private String refreshToken;

    /** Token 类型 */
    private String tokenType;

    /** 过期时间（秒） */
    private Long expiresIn;
}
