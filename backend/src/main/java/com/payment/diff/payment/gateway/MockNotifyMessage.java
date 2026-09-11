package com.payment.diff.payment.gateway;

import lombok.Data;

/**
 * 模拟渠道回调报文（与真实渠道"异步通知"报文同构）：
 * 交易流水号 + 渠道交易号 + 金额 + 支付结果 + 时间戳 + HMAC-SHA256 签名。
 */
@Data
public class MockNotifyMessage {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";

    /** 商户支付流水号 */
    private String txnNo;

    /** 渠道交易号（成功后回写，唯一约束兜底幂等） */
    private String channelTxnNo;

    /** 回调金额（分）：成功回调必须与流水应付金额一致 */
    private Long amountCent;

    /** 支付结果：SUCCESS / FAILED */
    private String payResult;

    /** 失败原因（仅失败回调） */
    private String failReason;

    /** 渠道侧时间戳（毫秒） */
    private Long timestamp;

    /** HMAC-SHA256 签名 */
    private String sign;
}
