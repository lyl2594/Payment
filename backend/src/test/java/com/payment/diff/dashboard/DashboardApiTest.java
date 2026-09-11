package com.payment.diff.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.diff.order.DiffOrder;
import com.payment.diff.order.DiffOrderMapper;
import com.payment.diff.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 任务 6.1 + 6.2 验证（spec: payment-dashboard「汇总指标」「收款趋势」）：
 * 1. 汇总指标与种子数据一致（累计/今日收款、状态计数、成功率）；
 * 2. 近 N 日趋势按日聚合、无数据日补零、窗口外不计入；
 * 3. 空库返回零值不报错。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DiffOrderMapper diffOrderMapper;

    private String token;

    @BeforeEach
    void setup() throws Exception {
        diffOrderMapper.delete(null);
        if (token == null) {
            MvcResult result = mockMvc.perform(post("/api/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                    .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            token = body.get("data").get("token").asText();
        }
    }

    private void insertOrder(String suffix, String status, long amountCent, LocalDateTime paidAt) {
        DiffOrder order = new DiffOrder();
        order.setOrderNo("BJDB" + suffix);
        order.setShortCode("DASH" + suffix);
        order.setOriginalOrderNo("ORIG-DB-" + suffix);
        order.setProductName("看板演示商品");
        order.setAmountCent(amountCent);
        order.setStatus(status);
        order.setExpireAt(LocalDateTime.now().plusHours(24));
        order.setPaidAt(paidAt);
        order.setCreatedBy("tester");
        diffOrderMapper.insert(order);
    }

    private JsonNode getJson(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void summaryShouldMatchSeededData() throws Exception {
        LocalDateTime todayAm = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0));
        LocalDateTime todayPm = LocalDateTime.of(LocalDate.now(), LocalTime.of(12, 0));
        insertOrder("S1", OrderStatus.SUCCESS.name(), 9900L, todayAm);
        insertOrder("S2", OrderStatus.SUCCESS.name(), 5000L, todayPm);
        insertOrder("S3", OrderStatus.SUCCESS.name(), 20000L, LocalDate.now().minusDays(3).atTime(9, 0));
        insertOrder("S4", OrderStatus.SUCCESS.name(), 10000L, LocalDate.now().minusDays(10).atTime(9, 0));
        insertOrder("P1", OrderStatus.PENDING.name(), 8800L, null);
        insertOrder("C1", OrderStatus.CLOSED.name(), 6600L, null);

        JsonNode resp = getJson("/api/admin/dashboard/summary");
        assertThat(resp.get("code").asInt()).isZero();
        JsonNode data = resp.get("data");
        // 累计收款 = 9900 + 5000 + 20000 + 10000（含 7 日窗口外）
        assertThat(data.get("totalAmountCent").asLong()).isEqualTo(44900L);
        // 今日收款 = 9900 + 5000
        assertThat(data.get("todayAmountCent").asLong()).isEqualTo(14900L);
        assertThat(data.get("totalOrders").asLong()).isEqualTo(6L);
        assertThat(data.get("pendingCount").asLong()).isEqualTo(1L);
        assertThat(data.get("successCount").asLong()).isEqualTo(4L);
        assertThat(data.get("closedCount").asLong()).isEqualTo(1L);
        // 成功率 = 4 / (4 + 1) = 80%
        assertThat(data.get("successRate").asDouble()).isEqualTo(80.0);
    }

    @Test
    void trendShouldAggregateByDayAndFillZero() throws Exception {
        insertOrder("S1", OrderStatus.SUCCESS.name(), 9900L, LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0)));
        insertOrder("S2", OrderStatus.SUCCESS.name(), 5000L, LocalDateTime.of(LocalDate.now(), LocalTime.of(12, 0)));
        insertOrder("S3", OrderStatus.SUCCESS.name(), 20000L, LocalDate.now().minusDays(3).atTime(9, 0));
        // 7 日窗口外，不应计入
        insertOrder("S4", OrderStatus.SUCCESS.name(), 10000L, LocalDate.now().minusDays(10).atTime(9, 0));

        JsonNode resp = getJson("/api/admin/dashboard/trend?days=7");
        assertThat(resp.get("code").asInt()).isZero();
        JsonNode points = resp.get("data").get("points");
        assertThat(points.size()).isEqualTo(7);

        // 无数据日补零（昨日）
        JsonNode yesterday = points.get(5);
        assertThat(yesterday.get("date").asText()).isEqualTo(LocalDate.now().minusDays(1).toString());
        assertThat(yesterday.get("amountCent").asLong()).isZero();
        assertThat(yesterday.get("orderCount").asLong()).isZero();

        // 3 日前一笔
        JsonNode threeDaysAgo = points.get(3);
        assertThat(threeDaysAgo.get("amountCent").asLong()).isEqualTo(20000L);
        assertThat(threeDaysAgo.get("orderCount").asLong()).isEqualTo(1L);

        // 今日两笔
        JsonNode today = points.get(6);
        assertThat(today.get("date").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(today.get("amountCent").asLong()).isEqualTo(14900L);
        assertThat(today.get("orderCount").asLong()).isEqualTo(2L);
    }

    @Test
    void emptyDataShouldReturnZeros() throws Exception {
        JsonNode summary = getJson("/api/admin/dashboard/summary");
        JsonNode s = summary.get("data");
        assertThat(s.get("totalAmountCent").asLong()).isZero();
        assertThat(s.get("todayAmountCent").asLong()).isZero();
        assertThat(s.get("totalOrders").asLong()).isZero();
        assertThat(s.get("successRate").asDouble()).isZero();

        JsonNode trend = getJson("/api/admin/dashboard/trend?days=3");
        JsonNode points = trend.get("data").get("points");
        assertThat(points.size()).isEqualTo(3);
        for (JsonNode p : points) {
            assertThat(p.get("amountCent").asLong()).isZero();
            assertThat(p.get("orderCount").asLong()).isZero();
        }
    }
}
