package com.payment.diff.order;

import com.payment.diff.payment.PaymentTxn;
import lombok.Data;

import java.util.List;

/** 订单详情：订单全部字段 + 原订单信息 + 全部支付流水 */
@Data
public class OrderDetailResponse {
    private DiffOrder order;
    /** 原订单信息（归集展示用，直接取自订单上的冗余字段） */
    private String originalOrderNo;
    private Long originalAmountCent;
    private String originalProduct;
    /** 该订单全部支付流水（每次发起支付一条） */
    private List<PaymentTxn> txns;
}
