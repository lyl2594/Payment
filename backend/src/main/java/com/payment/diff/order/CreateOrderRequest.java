package com.payment.diff.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 创建补差价单请求。金额单位分（Long），禁止浮点。
 * originalOrderNo 必填（引用式关联，同一原订单可多笔）；expireHours 选填（默认 48）。
 */
public record CreateOrderRequest(
        @NotBlank(message = "原订单号不能为空") String originalOrderNo,
        Long originalAmountCent,
        String originalProduct,
        @NotBlank(message = "商品名称不能为空") String productName,
        String quotaDesc,
        String remark,
        @NotNull(message = "补差金额不能为空") @Positive(message = "补差金额必须大于0") Long amountCent,
        @PositiveOrZero Integer expireHours) {

    public static final int DEFAULT_EXPIRE_HOURS = 48;
    public static final int MAX_EXPIRE_HOURS = 24 * 30;

    public int resolveExpireHours() {
        if (expireHours == null) {
            return DEFAULT_EXPIRE_HOURS;
        }
        if (expireHours > MAX_EXPIRE_HOURS) {
            throw new IllegalArgumentException("有效期最长 720 小时");
        }
        return expireHours;
    }
}
