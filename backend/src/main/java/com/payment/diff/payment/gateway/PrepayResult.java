package com.payment.diff.payment.gateway;

/**
 * 预下单结果：收银台跳转地址（前端 /cashier/:txnNo）。
 */
public record PrepayResult(String cashierUrl) {
}
