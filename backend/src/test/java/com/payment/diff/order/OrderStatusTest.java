package com.payment.diff.order;

import com.payment.diff.common.exception.BusinessException;
import com.payment.diff.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务 3.1（纯单元部分）：状态机显式守卫——仅 PENDING 可流转。
 */
class OrderStatusTest {

    @Test
    void onlyPendingCanTransition() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SUCCESS)).isTrue();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CLOSED)).isTrue();
        assertThat(OrderStatus.SUCCESS.canTransitionTo(OrderStatus.SUCCESS)).isFalse();
        assertThat(OrderStatus.SUCCESS.canTransitionTo(OrderStatus.CLOSED)).isFalse();
        assertThat(OrderStatus.CLOSED.canTransitionTo(OrderStatus.SUCCESS)).isFalse();
        assertThat(OrderStatus.CLOSED.canTransitionTo(OrderStatus.CLOSED)).isFalse();
    }

    @Test
    void guardMessageShouldBeBusinessException() {
        BusinessException e = new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_STATE_INVALID);
    }
}
