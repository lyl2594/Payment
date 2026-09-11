package com.payment.diff.link;

import com.payment.diff.order.DiffOrder;
import com.payment.diff.order.DiffOrderMapper;
import com.payment.diff.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务 4.2 + 4.3 验证（spec: short-link-access「短链跳转」「短链换取订单信息」「短链有效期跟随订单」）：
 * 1. GET /s/{code} → 302 到 H5 路由；
 * 2. 待支付订单返回完整信息（金额服务端下发 + 剩余秒数）；
 * 3. 已关闭订单返回失效提示（21001）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LinkApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DiffOrderMapper diffOrderMapper;

    private String insertOrder(String suffix, String status, LocalDateTime expireAt) {
        DiffOrder order = new DiffOrder();
        order.setOrderNo("BJLINK" + suffix);
        order.setShortCode("LINK" + suffix);
        order.setOriginalOrderNo("ORIG-" + suffix);
        order.setProductName("会员升级服务");
        order.setQuotaDesc("名额 1 个");
        order.setAmountCent(9900L);
        order.setStatus(status);
        order.setExpireAt(expireAt);
        order.setCreatedBy("tester");
        diffOrderMapper.insert(order);
        return order.getShortCode();
    }

    @Test
    void shortLinkShould302ToH5PayPage() throws Exception {
        String code = insertOrder("P1", OrderStatus.PENDING.name(), LocalDateTime.now().plusHours(1));
        mockMvc.perform(get("/s/" + code))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/pay/" + code));
    }

    @Test
    void pendingOrderShouldReturnFullInfoWithRemainSeconds() throws Exception {
        String code = insertOrder("P2", OrderStatus.PENDING.name(), LocalDateTime.now().plusHours(2));
        mockMvc.perform(get("/api/open/orders/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.shortCode").value(code))
                .andExpect(jsonPath("$.data.productName").value("会员升级服务"))
                .andExpect(jsonPath("$.data.quotaDesc").value("名额 1 个"))
                .andExpect(jsonPath("$.data.amountCent").value(9900))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.remainSeconds").isNumber());
    }

    @Test
    void closedOrderShouldReturnInvalidTip() throws Exception {
        String code = insertOrder("C1", OrderStatus.CLOSED.name(), LocalDateTime.now().plusHours(1));
        mockMvc.perform(get("/api/open/orders/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(21001));
    }

    @Test
    void unknownShortCodeShouldReturnInvalidTip() throws Exception {
        mockMvc.perform(get("/api/open/orders/ZZZZZZZZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(21001));
    }

    @Test
    void expiredPendingOrderShouldBeLazilyClosedAndInvalid() throws Exception {
        String code = insertOrder("E1", OrderStatus.PENDING.name(), LocalDateTime.now().minusSeconds(10));
        mockMvc.perform(get("/api/open/orders/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(21001));
        // 惰性关单生效
        DiffOrder after = diffOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DiffOrder>()
                        .eq(DiffOrder::getShortCode, code));
        org.assertj.core.api.Assertions.assertThat(after.getStatus()).isEqualTo(OrderStatus.CLOSED.name());
    }
}
