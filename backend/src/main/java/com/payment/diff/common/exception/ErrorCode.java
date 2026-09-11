package com.payment.diff.common.exception;

/**
 * 业务错误码枚举。编号按领域分段：
 * 0 成功；10xxx 通用/系统；20xxx 订单；21xxx 短链；22xxx 支付流水；23xxx 渠道/回调；24xxx 后台账号。
 */
public enum ErrorCode {

    SUCCESS(0, "ok"),

    // ---- 通用/系统 10xxx ----
    SYSTEM_ERROR(10000, "系统繁忙，请稍后重试"),
    PARAM_INVALID(10001, "参数错误"),
    UNAUTHORIZED(10401, "未登录或登录已过期"),

    // ---- 订单 20xxx ----
    ORDER_NOT_FOUND(20001, "订单不存在"),
    ORDER_CLOSED(20002, "订单已关闭"),
    ORDER_STATE_INVALID(20003, "订单状态不允许该操作"),
    ORDER_EXPIRED(20004, "订单已过期，支付无效"),

    // ---- 短链 21xxx ----
    SHORT_CODE_INVALID(21001, "短链无效或已失效"),

    // ---- 支付流水 22xxx ----
    TXN_NOT_FOUND(22001, "支付流水不存在"),
    TXN_STATE_INVALID(22002, "支付流水状态不允许该操作"),

    // ---- 渠道/回调 23xxx ----
    SIGN_INVALID(23001, "签名验证失败"),
    AMOUNT_MISMATCH(23002, "回调金额与订单不一致"),

    // ---- 后台账号 24xxx ----
    LOGIN_FAILED(24001, "账号或密码错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
