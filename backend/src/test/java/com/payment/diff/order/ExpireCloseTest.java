package com.payment.diff.order;

import com.payment.diff.common.exception.BusinessException;
import com.payment.diff.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 任务 3.5 验证（spec: diff-order-management「超时自动关单」双保险两场景）：
 * 1. 定时扫描关闭超时订单；
 * 2. 已超时未扫描订单收到成功回调 → 拒绝入账并置为已关闭。
 */
@SpringBootTest
@ActiveProfiles("test")
class ExpireCloseTest {

    @Autowired
    private DiffOrderMapper diffOrderMapper;

    @Autowired
    private DiffOrderService diffOrderService;

    private long insertExpiredPendingOrder(String orderNo, LocalDateTime expireAt) {
        DiffOrder order = new DiffOrder();
        order.setOrderNo(orderNo);
        order.setShortCode("EX" + orderNo.substring(2));
        order.setOriginalOrderNo("ORIG-" + orderNo);
        order.setProductName("测试商品");
        order.setAmountCent(100L);
        order.setStatus(OrderStatus.PENDING.name());
        order.setExpireAt(expireAt);
        order.setCreatedBy("tester");
        diffOrderMapper.insert(order);
        return order.getId();
    }

    @Test
    void schedulerShouldCloseExpiredPendingOrder() {
        long id = insertExpiredPendingOrder("BJTEST020001", LocalDateTime.now().minusMinutes(1));

        int closed = diffOrderService.closeExpiredOrders();

        assertThat(closed).isGreaterThanOrEqualTo(1);
        assertThat(diffOrderMapper.selectById(id).getStatus()).isEqualTo(OrderStatus.CLOSED.name());
    }

    @Test
    void lateSuccessCallbackOnExpiredOrderShouldBeRejectedAndClosed() {
        // 已超时但尚未被扫描关闭的 PENDING 订单
        long id = insertExpiredPendingOrder("BJTEST020002", LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> diffOrderService.markSuccess(id))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_EXPIRED));

        // 被惰性关单，不产生已支付状态
        DiffOrder after = diffOrderMapper.selectById(id);
        assertThat(after.getStatus()).isEqualTo(OrderStatus.CLOSED.name());
        assertThat(after.getPaidAt()).isNull();
    }
}
