package com.payment.diff.payment;

/**
 * 支付流水状态机：CREATED → SUCCESS / FAILED（终态）。
 * 失败后重试支付会创建新流水，不复用失败流水。
 */
public enum TxnStatus {

    CREATED,
    SUCCESS,
    FAILED
}
