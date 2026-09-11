package com.payment.diff.order;

import java.time.LocalDateTime;

/** 创建成功响应：唯一订单号 + 支付短链 */
public record CreateOrderResponse(
        String orderNo,
        String shortCode,
        String shortUrl,
        String status,
        LocalDateTime expireAt) {
}
