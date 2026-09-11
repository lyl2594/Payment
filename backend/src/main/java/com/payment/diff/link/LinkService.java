package com.payment.diff.link;

import com.payment.diff.common.exception.BusinessException;
import com.payment.diff.common.exception.ErrorCode;
import com.payment.diff.order.DiffOrder;
import com.payment.diff.order.DiffOrderMapper;
import com.payment.diff.order.OrderStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 短链服务：短码换订单（含失效判定）。短链可用性跟随订单：仅待支付且未超期可用。
 */
@Service
@RequiredArgsConstructor
public class LinkService {

    private final DiffOrderMapper diffOrderMapper;

    @Value("${diffpay.pay.h5-base-url}")
    private String h5BaseUrl;

    /** 按短码取订单；不存在、已关闭或已过期（惰性关单）→ 统一提示短链失效 */
    public DiffOrder requireValidOrderByShortCode(String shortCode) {
        DiffOrder order = diffOrderMapper.selectOne(new LambdaQueryWrapper<DiffOrder>()
                .eq(DiffOrder::getShortCode, shortCode));
        if (order == null) {
            throw new BusinessException(ErrorCode.SHORT_CODE_INVALID);
        }
        if (OrderStatus.CLOSED.name().equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.SHORT_CODE_INVALID);
        }
        // 待支付但已超期：惰性关单后按失效处理（与关单双保险一致）
        if (order.isPending() && order.isExpired(LocalDateTime.now())) {
            diffOrderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DiffOrder>()
                    .eq(DiffOrder::getId, order.getId())
                    .eq(DiffOrder::getStatus, OrderStatus.PENDING.name())
                    .set(DiffOrder::getStatus, OrderStatus.CLOSED.name()));
            throw new BusinessException(ErrorCode.SHORT_CODE_INVALID);
        }
        return order;
    }

    /** H5 支付页地址（302 目标） */
    public String h5PayUrl(String shortCode) {
        return h5BaseUrl + "/pay/" + shortCode;
    }
}
