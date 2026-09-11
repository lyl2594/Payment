package com.payment.diff.dashboard;

/**
 * 趋势单日数据点：日期（yyyy-MM-dd）、收款金额（分）、成交订单数。无数据日补零。
 */
public record DailyPoint(String date, long amountCent, long orderCount) {
}
