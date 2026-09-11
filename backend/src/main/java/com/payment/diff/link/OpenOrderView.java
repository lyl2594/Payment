package com.payment.diff.link;

import com.payment.diff.order.DiffOrder;

import java.time.LocalDateTime;

/**
 * 短链换订单信息视图。金额以服务端存储值为准下发（客户端无金额传参）。
 */
public record OpenOrderView(
        String shortCode,
        String productName,
        String quotaDesc,
        Long amountCent,
        String status,
        LocalDateTime expireAt,
        long remainSeconds) {

    public static OpenOrderView from(DiffOrder order, long remainSeconds) {
        return new OpenOrderView(order.getShortCode(), order.getProductName(), order.getQuotaDesc(),
                order.getAmountCent(), order.getStatus(), order.getExpireAt(), remainSeconds);
    }
}
