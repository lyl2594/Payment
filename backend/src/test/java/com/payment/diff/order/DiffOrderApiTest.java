package com.payment.diff.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.diff.admin.JwtService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务 3.2 + 3.3 验证（spec: diff-order-management「基于原订单创建」「订单列表与详情」）。
 * 场景：成功创建 / 同一原订单多笔 / 缺原订单号被拒 / 金额非法被拒 / 缺必填被拒 / 原订单号归集 / 详情含原订单信息与流水。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiffOrderApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void login() {
        token = jwtService.issue("admin", "ADMIN");
    }

    /** MockMvc 响应体统一按 UTF-8 读取（默认 ISO-8859-1 会导致中文乱码） */
    private static String body(MvcResult result) throws Exception {
        result.getResponse().setCharacterEncoding(StandardCharsets.UTF_8.name());
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private MvcResult createOrder(String json) throws Exception {
        return mockMvc.perform(post("/api/admin/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static final String CREATE_BODY = """
            {
              "originalOrderNo": "%s",
              "productName": "%s",
              "amountCent": %s,
              "quotaDesc": "升级名额 1 个",
              "originalAmountCent": %s,
              "originalProduct": "%s"
            }
            """;

    @Test
    void createOrderShouldReturnOrderNoAndShortLink() throws Exception {
        MvcResult result = createOrder(CREATE_BODY.formatted(
                "ORIG-A-001", "云盘会员升级", "9900", "19900", "云盘基础会员"));
        JsonNode data = objectMapper.readTree(body(result)).get("data");
        assertThat(data.get("orderNo").asText()).startsWith("BJ").hasSize(16);
        assertThat(data.get("shortCode").asText()).hasSize(8).matches("[0-9A-Za-z]{8}");
        assertThat(data.get("shortUrl").asText()).endsWith("/" + data.get("shortCode").asText());
        assertThat(data.get("status").asText()).isEqualTo("PENDING");

        // 详情可查到创建人与原订单关联
        MvcResult detail = mockMvc.perform(get("/api/admin/orders/" + data.get("orderNo").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode detailData = objectMapper.readTree(body(detail)).get("data");
        assertThat(detailData.get("originalOrderNo").asText()).isEqualTo("ORIG-A-001");
        assertThat(detailData.get("originalAmountCent").asLong()).isEqualTo(19900L);
        assertThat(detailData.get("order").get("createdBy").asText()).isEqualTo("admin");
    }

    @Test
    void sameOriginalOrderNoShouldAllowMultipleOrders() throws Exception {
        createOrder(CREATE_BODY.formatted("ORIG-A-002", "会员升级第一期", "5000", "10000", "基础版"));
        createOrder(CREATE_BODY.formatted("ORIG-A-002", "会员升级第二期", "6000", "10000", "基础版"));

        MvcResult list = mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", "Bearer " + token)
                        .param("originalOrderNo", "ORIG-A-002"))
                .andExpect(status().isOk()).andReturn();
        JsonNode records = objectMapper.readTree(body(list)).get("data").get("records");
        assertThat(records.size()).isEqualTo(2);
    }

    @Test
    void missingOriginalOrderNoShouldBeRejected() throws Exception {
        String body = "{\"productName\":\"x\",\"amountCent\":100}";
        JsonNode node = objectMapper.readTree(body(createOrder(body)));
        assertThat(node.get("code").asInt()).isEqualTo(10001);
        assertThat(node.get("message").asText()).contains("原订单号");
    }

    @Test
    void invalidAmountShouldBeRejected() throws Exception {
        for (String amount : new String[]{"0", "-100"}) {
            JsonNode node = objectMapper.readTree(body(createOrder(
                    CREATE_BODY.formatted("ORIG-A-003", "商品", amount, "null", "原商品"))));
            assertThat(node.get("code").asInt()).isEqualTo(10001);
            assertThat(node.get("message").asText()).contains("补差金额");
        }
    }

    @Test
    void missingProductNameShouldBeRejected() throws Exception {
        String body = "{\"originalOrderNo\":\"ORIG-X\",\"amountCent\":100}";
        JsonNode node = objectMapper.readTree(body(createOrder(body)));
        assertThat(node.get("code").asInt()).isEqualTo(10001);
        assertThat(node.get("message").asText()).contains("商品名称");
    }

    @Test
    void detailShouldContainOriginalInfoAndTxns() throws Exception {
        // 创建订单并查询详情（流水结构就位；多条流水场景在任务 5.x 回调测试中覆盖）
        MvcResult created = createOrder(CREATE_BODY.formatted("ORIG-A-004", "直播会员", "8800", "null", ""));
        String orderNo = objectMapper.readTree(body(created)).get("data").get("orderNo").asText();

        MvcResult detail = mockMvc.perform(get("/api/admin/orders/" + orderNo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode node = objectMapper.readTree(body(detail)).get("data");
        assertThat(node.get("txns").isArray()).isTrue();
        assertThat(node.get("originalOrderNo").asText()).isEqualTo("ORIG-A-004");
    }
}
