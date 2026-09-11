package com.payment.diff.order;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 订单号/流水号生成：前缀 + yyyyMMdd + 6位序列（序列取自数据库自增 id，应用层格式化）。
 */
public final class BusinessNoFormatter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private BusinessNoFormatter() {
    }

    /** 由数据库自增 id 格式化业务单号：BJ/TX + yyyyMMdd + %06d */
    public static String format(String prefix, Long id) {
        return prefix + LocalDate.now().format(DATE) + String.format("%06d", id);
    }
}
