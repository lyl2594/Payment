package com.payment.diff.payment;

/**
 * 发起支付响应：流水号 + 收银台地址。金额以服务端订单值为权威，不下发可改参数。
 */
public record PayCreateResponse(
        String txnNo,
        String txnStatus,
        Long amountCent,
        String orderNo,
        String cashierUrl) {
}
