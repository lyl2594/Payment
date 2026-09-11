package com.payment.diff.dashboard;

import lombok.Data;

import java.time.LocalDate;

/**
 * 按日聚合行（SQL 查询结果映射）。
 */
@Data
public class DailyStatRow {

    /** 日期（paid_at 所在日）；别名 stat_day（day 为 H2/PG 保留字，不可作列别名） */
    private LocalDate statDay;

    /** 成交订单数 */
    private Long orderCount;

    /** 收款金额（分） */
    private Long amountCent;
}
