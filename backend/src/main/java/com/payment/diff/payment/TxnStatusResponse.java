package com.payment.diff.payment;

import java.time.LocalDateTime;

/**
 * 流水状态查询响应（H5 轮询用）：含终态、失败原因与关联订单状态。
 */
public record TxnStatusResponse(
        String txnNo,
        String txnStatus,
        Long amountCent,
        String failReason,
        LocalDateTime successAt,
        String orderNo,
        String orderStatus) {
}
