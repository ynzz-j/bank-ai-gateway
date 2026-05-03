package com.bank.ai.gateway.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * <p>用于业务逻辑中抛出可预期的异常，由全局异常处理器捕获并返回统一格式。
 *
 * @since 1.0.0
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final String message;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.message = message;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}