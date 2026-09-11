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
 * 任务 3.4 验证（spec: diff-order-management「手动关单」两场景）：
 * 1. 关闭待支付订单成功；
 * 2. 关闭已支付订单被拒。
 */
@SpringBootTest
@ActiveProfiles("test")
class ManualCloseTest {

    @Autowired
    private DiffOrderMapper diffOrderMapper;

    @Autowired
    private DiffOrderService diffOrderService;

    private void insertOrder(String orderNo, String status) {
        DiffOrder order = new DiffOrder();
        order.setOrderNo(orderNo);
        order.setShortCode("MC" + orderNo.substring(2));
        order.setOriginalOrderNo("ORIG-" + orderNo);
        order.setProductName("测试商品");
        order.setAmountCent(100L);
        order.setStatus(status);
        order.setExpireAt(LocalDateTime.now().plusHours(48));
        order.setCreatedBy("tester");
        diffOrderMapper.insert(order);
    }

    @Test
    void closePendingOrderShouldSucceed() {
        insertOrder("BJTEST010001", OrderStatus.PENDING.name());
        diffOrderService.closeByOrderNo("BJTEST010001", "admin");
        assertThat(diffOrderMapper.selectById(
                diffOrderService.requireOrderByNo("BJTEST010001").getId()).getStatus())
                .isEqualTo(OrderStatus.CLOSED.name());
    }

    @Test
    void closePaidOrderShouldBeRejected() {
        insertOrder("BJTEST010002", OrderStatus.SUCCESS.name());
        assertThatThrownBy(() -> diffOrderService.closeByOrderNo("BJTEST010002", "admin"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID));
        assertThat(diffOrderService.requireOrderByNo("BJTEST010002").getStatus())
                .isEqualTo(OrderStatus.SUCCESS.name());
    }
}
