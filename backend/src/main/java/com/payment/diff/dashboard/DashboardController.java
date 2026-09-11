package com.payment.diff.dashboard;

import com.payment.diff.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据看板接口（JWT 保护）：汇总指标 + 近 N 日趋势。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<SummaryResponse> summary() {
        return ApiResponse.ok(dashboardService.summary());
    }

    @GetMapping("/trend")
    public ApiResponse<TrendResponse> trend(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(dashboardService.trend(Math.min(Math.max(days, 1), 90)));
    }
}
