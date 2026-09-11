package com.payment.diff.dashboard;

import java.util.List;

/**
 * 收款趋势响应：近 N 日每日收款金额与成交订单数。
 */
public record TrendResponse(int days, List<DailyPoint> points) {
}
