package com.payment.diff.payment;

import jakarta.validation.constraints.NotBlank;

/**
 * 模拟收银台用户操作请求（渠道侧接收）。金额不出现在请求中：应付金额以流水记录为准。
 */
public record MockPayRequest(
        @NotBlank(message = "txnNo 不能为空") String txnNo,
        @NotBlank(message = "result 不能为空") String result) {
}
