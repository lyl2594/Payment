package com.payment.diff.order;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.payment.diff.common.api.ApiResponse;
import com.payment.diff.common.web.AuthConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 后台订单管理接口（JWT 保护）。
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class DiffOrderController {

    private final DiffOrderService diffOrderService;

    @PostMapping
    public ApiResponse<CreateOrderResponse> create(@Valid @RequestBody CreateOrderRequest request,
                                                   HttpServletRequest httpRequest) {
        String operator = (String) httpRequest.getAttribute(AuthConstants.ATTR_USERNAME);
        return ApiResponse.ok(diffOrderService.create(request, operator));
    }

    @GetMapping
    public ApiResponse<Page<DiffOrder>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String originalOrderNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.ok(diffOrderService.page(page, size, status, keyword, originalOrderNo, beginTime, endTime));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDetailResponse> detail(@PathVariable String orderNo) {
        return ApiResponse.ok(diffOrderService.detail(orderNo));
    }

    @PostMapping("/{orderNo}/close")
    public ApiResponse<Void> close(@PathVariable String orderNo, HttpServletRequest httpRequest) {
        String operator = (String) httpRequest.getAttribute(AuthConstants.ATTR_USERNAME);
        diffOrderService.closeByOrderNo(orderNo, operator);
        return ApiResponse.ok();
    }
}
