package com.payment.diff.payment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付流水：一笔订单可多次发起支付（失败重试生成新流水）。
 * channel_txn_no 唯一约束是回调幂等第二道防线（渠道可空：仅支付成功后回写）。
 */
@Data
@TableName("payment_txn")
public class PaymentTxn {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** TX + yyyyMMdd + 6位序列 */
    private String txnNo;

    private Long orderId;

    /** 应付金额（分），发起时从订单取值 */
    private Long amountCent;

    /** CREATED / SUCCESS / FAILED */
    private String status;

    /** 渠道：MOCK */
    private String channel;

    /** 渠道交易号（成功后回写，唯一） */
    private String channelTxnNo;

    private String failReason;

    private LocalDateTime successAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
