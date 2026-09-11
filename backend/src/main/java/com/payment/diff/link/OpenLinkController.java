package com.payment.diff.link;

import com.payment.diff.common.api.ApiResponse;
import com.payment.diff.order.DiffOrder;
import com.payment.diff.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 开放 API（匿名，凭短码）：H5 支付页换取订单信息。
 */
@RestController
@RequestMapping("/api/open")
@RequiredArgsConstructor
public class OpenLinkController {

    private final LinkService linkService;

    @GetMapping("/orders/{shortCode}")
    public ApiResponse<OpenOrderView> byShortCode(@PathVariable String shortCode) {
        DiffOrder order = linkService.requireValidOrderByShortCode(shortCode);
        long remainSeconds = 0;
        if (OrderStatus.PENDING.name().equals(order.getStatus())) {
            remainSeconds = Math.max(0, java.time.Duration.between(
                    LocalDateTime.now(), order.getExpireAt()).toSeconds());
        }
        return ApiResponse.ok(OpenOrderView.from(order, remainSeconds));
    }
}
