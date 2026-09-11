package com.payment.diff.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 补差价单。金额一律 Long 分；original_order_no 为必填业务关联键（引用式，不做存在性校验）。
 * refund_status / fulfill_status 为 non-goal 预留列。
 */
@Data
@TableName("diff_order")
public class DiffOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** BJ + yyyyMMdd + 6位序列 */
    private String orderNo;

    /** 8 位 Base62 随机短码（唯一） */
    private String shortCode;

    /** 原订单号（必填，普通索引，同一原订单 1:N） */
    private String originalOrderNo;

    /** 原订单金额（分，选填） */
    private Long originalAmountCent;

    /** 原商品名称（选填） */
    private String originalProduct;

    /** 商品/服务名称（必填） */
    private String productName;

    /** 名额说明（仅描述信息，非库存） */
    private String quotaDesc;

    private String remark;

    /** 补差金额（分，必填 >0） */
    private Long amountCent;

    /** PENDING / SUCCESS / CLOSED */
    private String status;

    /** 有效期截止时间（默认创建 +48h） */
    private LocalDateTime expireAt;

    private String createdBy;

    private LocalDateTime paidAt;

    /** 退款预留 */
    private String refundStatus;

    /** 履约预留 */
    private String fulfillStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ---- 状态便捷判断 ----

    public boolean isPending() {
        return OrderStatus.PENDING.name().equals(status);
    }

    public boolean isSuccess() {
        return OrderStatus.SUCCESS.name().equals(status);
    }

    public boolean isExpired(LocalDateTime now) {
        return expireAt != null && expireAt.isBefore(now);
    }
}
