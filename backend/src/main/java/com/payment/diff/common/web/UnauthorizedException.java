package com.payment.diff.common.web;

import com.payment.diff.common.exception.ErrorCode;

/**
 * 鉴权失败异常：由全局异常处理器映射为 HTTP 401。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super(ErrorCode.UNAUTHORIZED.getMessage());
    }

    public UnauthorizedException(String message) {
        super(message);
    }
}
