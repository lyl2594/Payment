package com.payment.diff.order;

/**
 * 订单状态机：PENDING → SUCCESS | CLOSED。
 * SUCCESS/CLOSED 为终态，仅 PENDING 可流转。
 */
public enum OrderStatus {

    PENDING,
    SUCCESS,
    CLOSED;

    /** 是否允许从当前状态流转到目标状态（显式守卫，非法流转一律拒绝） */
    public boolean canTransitionTo(OrderStatus target) {
        return this == PENDING && (target == SUCCESS || target == CLOSED);
    }
}
