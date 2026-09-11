package com.payment.diff.dashboard;

/**
 * 看板汇总指标响应。
 * 支付成功率（订单维度）= 已支付 / (已支付 + 已关闭)：待支付订单尚未定性，不计入分母。
 */
public record SummaryResponse(
        long totalAmountCent,
        long todayAmountCent,
        long totalOrders,
        long pendingCount,
        long successCount,
        long closedCount,
        double successRate) {
}
