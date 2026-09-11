package com.payment.diff.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务 1.4 验证：同一份可移植 schema.sql 在 H2（内存库，与默认 profile 共用脚本）上建表成功，
 * 唯一索引与预留列就位，种子管理员 admin/admin123（BCrypt）创建且播种幂等。
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaAndSeedTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminSeed adminSeed;

    @Test
    void threeTablesShouldExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                String.class).stream().map(String::toUpperCase).collect(Collectors.toList());
        assertThat(tables).contains("ADMIN_USER", "DIFF_ORDER", "PAYMENT_TXN");
    }

    @Test
    void uniqueConstraintsShouldExist() {
        // channel_txn_no 唯一约束 = 回调幂等防线2；short_code 唯一约束 = 短码安全底线
        Integer shortCodeUk = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
                        "WHERE TABLE_NAME = 'DIFF_ORDER' AND CONSTRAINT_NAME = 'UK_DIFF_ORDER_SHORT_CODE' AND CONSTRAINT_TYPE = 'UNIQUE'",
                Integer.class);
        Integer channelTxnUk = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
                        "WHERE TABLE_NAME = 'PAYMENT_TXN' AND CONSTRAINT_NAME = 'UK_PAYMENT_TXN_CHANNEL_TXN_NO' AND CONSTRAINT_TYPE = 'UNIQUE'",
                Integer.class);
        assertThat(shortCodeUk).isEqualTo(1);
        assertThat(channelTxnUk).isEqualTo(1);
    }

    @Test
    void requiredAndReservedColumnsShouldExist() {
        List<String> cols = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'DIFF_ORDER'",
                String.class).stream().map(String::toUpperCase).collect(Collectors.toList());
        // 必填业务列
        assertThat(cols).contains("ORDER_NO", "SHORT_CODE", "ORIGINAL_ORDER_NO",
                "PRODUCT_NAME", "AMOUNT_CENT", "STATUS", "EXPIRE_AT", "CREATED_BY");
        // 退款/履约预留列（non-goal 预留）
        assertThat(cols).contains("REFUND_STATUS", "FULFILL_STATUS");
        // 原订单关联选填列
        assertThat(cols).contains("ORIGINAL_AMOUNT_CENT", "ORIGINAL_PRODUCT");
    }

    @Test
    void seedAdminShouldBeCreatedWithBcryptPassword() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM admin_user WHERE username = 'admin'");
        assertThat(rows).hasSize(1);
        assertThat(new BCryptPasswordEncoder().matches("admin123", (String) rows.get(0).get("PASSWORD_HASH")))
                .as("BCrypt(admin123) 应与种子摘要匹配")
                .isTrue();
    }

    @Test
    void seedShouldBeIdempotentWhenRunAgain() {
        // 再次执行播种逻辑：不应产生第二条 admin 记录
        adminSeed.run(null);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_user WHERE username = 'admin'", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
