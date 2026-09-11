package com.payment.diff.dashboard;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 看板聚合查询（原生 SQL，H2/PG 兼容：CAST AS DATE 两方言均支持）。
 */
@Mapper
public interface DashboardMapper {

    String SUCCESS_STATUS = "SUCCESS";

    @Select("SELECT COUNT(*) FROM diff_order")
    long countAll();

    @Select("SELECT COUNT(*) FROM diff_order WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    @Select("SELECT COALESCE(SUM(amount_cent), 0) FROM diff_order WHERE status = 'SUCCESS'")
    long sumSuccessAmount();

    @Select("SELECT COALESCE(SUM(amount_cent), 0) FROM diff_order "
            + "WHERE status = 'SUCCESS' AND paid_at >= #{begin}")
    long sumSuccessAmountSince(@Param("begin") LocalDateTime begin);

    @Select("SELECT CAST(paid_at AS DATE) AS stat_day, COUNT(*) AS order_count, "
            + "COALESCE(SUM(amount_cent), 0) AS amount_cent "
            + "FROM diff_order WHERE status = 'SUCCESS' AND paid_at >= #{begin} "
            + "GROUP BY CAST(paid_at AS DATE)")
    List<DailyStatRow> sumSuccessByDay(@Param("begin") LocalDateTime begin);
}
