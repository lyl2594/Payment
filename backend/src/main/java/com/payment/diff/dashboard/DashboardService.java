package com.payment.diff.dashboard;

import com.payment.diff.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据看板服务：汇总指标与近 N 日收款趋势（无数据日补零）。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper dashboardMapper;

    public SummaryResponse summary() {
        long total = dashboardMapper.countAll();
        long success = dashboardMapper.countByStatus(OrderStatus.SUCCESS.name());
        long closed = dashboardMapper.countByStatus(OrderStatus.CLOSED.name());
        long pending = dashboardMapper.countByStatus(OrderStatus.PENDING.name());
        long totalAmount = dashboardMapper.sumSuccessAmount();
        long todayAmount = dashboardMapper.sumSuccessAmountSince(LocalDate.now().atStartOfDay());
        double successRate = (success + closed) == 0
                ? 0.0
                : Math.round(success * 10000.0 / (success + closed)) / 100.0;
        return new SummaryResponse(totalAmount, todayAmount, total, pending, success, closed, successRate);
    }

    public TrendResponse trend(int days) {
        LocalDate today = LocalDate.now();
        LocalDate begin = today.minusDays(days - 1L);
        Map<LocalDate, DailyStatRow> byDay = dashboardMapper
                .sumSuccessByDay(begin.atStartOfDay()).stream()
                .collect(Collectors.toMap(DailyStatRow::getStatDay, Function.identity()));
        List<DailyPoint> points = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate d = begin.plusDays(i);
            DailyStatRow row = byDay.get(d);
            points.add(new DailyPoint(d.toString(),
                    row == null ? 0L : row.getAmountCent(),
                    row == null ? 0L : row.getOrderCount()));
        }
        return new TrendResponse(days, points);
    }
}
