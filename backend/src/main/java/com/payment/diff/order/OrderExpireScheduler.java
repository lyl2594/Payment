package com.payment.diff.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时关单定时任务：每分钟扫描过期 PENDING 订单置为 CLOSED。
 * 与回调惰性校验构成双保险（资金安全不依赖任务执行的准时性）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireScheduler {

    private final DiffOrderService diffOrderService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void closeExpired() {
        int closed = diffOrderService.closeExpiredOrders();
        if (closed > 0) {
            log.info("定时扫描关单: {} 笔过期订单被关闭", closed);
        }
    }
}
