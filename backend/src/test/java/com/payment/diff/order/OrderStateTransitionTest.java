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
 * 任务 3.1 验证（spec: diff-order-management「订单状态机」）：
 * 1. 待支付 → 已支付成功，记录支付时间；
 * 2. 已支付再支付 → 业务异常且数据不变（重复请求不产生重复入账）；
 * 3. 已关闭入账 → 业务异常且数据不变。
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderStateTransitionTest {

    @Autowired
    private DiffOrderMapper diffOrderMapper;

    @Autowired
    private DiffOrderService diffOrderService;

    private long insertPendingOrder(String orderNo) {
        DiffOrder order = new DiffOrder();
        order.setOrderNo(orderNo);
        order.setShortCode("TC" + orderNo.substring(2));
        order.setOriginalOrderNo("ORIG-" + orderNo);
        order.setProductName("测试商品");
        order.setAmountCent(10000L);
        order.setStatus(OrderStatus.PENDING.name());
        order.setExpireAt(LocalDateTime.now().plusHours(48));
        order.setCreatedBy("tester");
        diffOrderMapper.insert(order);
        return order.getId();
    }

    @Test
    void pendingToSuccessShouldRecordPaidAt() {
        long id = insertPendingOrder("BJTEST000001");
        boolean first = diffOrderService.markSuccess(id);
        assertThat(first).isTrue();

        DiffOrder after = diffOrderMapper.selectById(id);
        assertThat(after.getStatus()).isEqualTo(OrderStatus.SUCCESS.name());
        assertThat(after.getPaidAt()).isNotNull();
    }

    @Test
    void markSuccessAgainShouldRejectAndKeepData() {
        long id = insertPendingOrder("BJTEST000002");
        diffOrderService.markSuccess(id);
        LocalDateTime paidAt = diffOrderMapper.selectById(id).getPaidAt();

        assertThatThrownBy(() -> diffOrderService.markSuccess(id))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID));

        // 数据不变：状态与支付时间均未被改动
        DiffOrder after = diffOrderMapper.selectById(id);
        assertThat(after.getStatus()).isEqualTo(OrderStatus.SUCCESS.name());
        assertThat(after.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    void closedOrderShouldRejectIncome() {
        long id = insertPendingOrder("BJTEST000003");
        diffOrderService.closeByOrderNo("BJTEST000003", "tester");

        assertThatThrownBy(() -> diffOrderService.markSuccess(id))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_CLOSED));

        DiffOrder after = diffOrderMapper.selectById(id);
        assertThat(after.getStatus()).isEqualTo(OrderStatus.CLOSED.name());
        assertThat(after.getPaidAt()).isNull();
    }
}
