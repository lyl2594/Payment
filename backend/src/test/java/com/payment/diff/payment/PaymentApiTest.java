package com.payment.diff.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.diff.order.DiffOrder;
import com.payment.diff.order.DiffOrderMapper;
import com.payment.diff.order.OrderStatus;
import com.payment.diff.payment.gateway.MockNotifyMessage;
import com.payment.diff.payment.gateway.MockSigner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务 5.1-5.5 验证（spec: h5-payment + mock-payment-channel）。
 * 使用 RANDOM_PORT 真实服务：模拟渠道端点可完成 HTTP 回环回调。
 * 覆盖：发起支付/篡改金额忽略、全链路成功入账、重复回调幂等、伪造签名拒绝、
 * 金额不一致拒绝、失败回调后重试生成新流水、非待支付订单拒绝发起支付。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DiffOrderMapper diffOrderMapper;

    @Autowired
    private PaymentTxnMapper paymentTxnMapper;

    @Value("${diffpay.pay.mock-channel-secret}")
    private String channelSecret;

    // ---------- 种子数据 ----------

    private String insertOrder(String suffix, String status, LocalDateTime expireAt) {
        DiffOrder order = new DiffOrder();
        order.setOrderNo("BJPAY" + suffix);
        order.setShortCode("PAYT" + suffix);
        order.setOriginalOrderNo("ORIG-" + suffix);
        order.setProductName("会员升级服务");
        order.setAmountCent(9900L);
        order.setStatus(status);
        order.setExpireAt(expireAt);
        order.setCreatedBy("tester");
        diffOrderMapper.insert(order);
        return order.getShortCode();
    }

    // ---------- 请求工具 ----------

    private JsonNode postJson(String url, Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        return objectMapper.readTree(resp.getBody());
    }

    private JsonNode getJson(String url) throws Exception {
        return objectMapper.readTree(rest.getForEntity(url, String.class).getBody());
    }

    /** 发起支付（body 可携带任意附加参数用于验证篡改金额被忽略） */
    private JsonNode pay(String shortCode, Map<String, Object> extraBody) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(extraBody == null ? Map.of() : extraBody);
        return postJson("/api/open/s/" + shortCode + "/pay", body);
    }

    /** 构造带合法签名的回调报文 */
    private MockNotifyMessage signedMsg(String txnNo, String channelTxnNo, Long amountCent,
                                        String result, String failReason) {
        MockNotifyMessage msg = new MockNotifyMessage();
        msg.setTxnNo(txnNo);
        msg.setChannelTxnNo(channelTxnNo);
        msg.setAmountCent(amountCent);
        msg.setPayResult(result);
        msg.setFailReason(failReason);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setSign(MockSigner.sign(msg, channelSecret));
        return msg;
    }

    private DiffOrder orderByShortCode(String shortCode) {
        return diffOrderMapper.selectOne(new LambdaQueryWrapper<DiffOrder>()
                .eq(DiffOrder::getShortCode, shortCode));
    }

    // ---------- 用例 ----------

    @Test
    void payShouldCreateTxnAndIgnoreTamperedAmount() throws Exception {
        String code = insertOrder("P1", OrderStatus.PENDING.name(), LocalDateTime.now().plusHours(1));
        // 附加篡改金额参数，服务端必须忽略并按订单金额创建流水
        JsonNode resp = pay(code, Map.of("amountCent", 1, "amount", 0.01));
        assertThat(resp.get("code").asInt()).isZero();
        JsonNode data = resp.get("data");
        assertThat(data.get("txnNo").asText()).startsWith("TX");
        assertThat(data.get("txnStatus").asText()).isEqualTo(TxnStatus.CREATED.name());
        assertThat(data.get("amountCent").asLong()).isEqualTo(9900L);
        assertThat(data.get("cashierUrl").asText())
                .contains("/cashier/" + data.get("txnNo").asText())
                .endsWith("?shortCode=" + code);
    }

    @Test
    void fullLoopShouldPostOrderOnSuccess() throws Exception {
        String code = insertOrder("P2", OrderStatus.PENDING.name(), LocalDateTime.now().plusHours(1));
        JsonNode payResp = pay(code, null);
        String txnNo = payResp.get("data").get("txnNo").asText();

        // 收银台操作：模拟渠道端点签名后回环投递回调
        JsonNode accepted = postJson("/api/mock/channel/pay", Map.of("txnNo", txnNo, "result", "SUCCESS"));
        assertThat(accepted.get("code").asInt()).isZero();

        // H5 轮询流水：终态成功
        JsonNode txn = getJson("/api/open/txns/" + txnNo);
        assertThat(txn.get("data").get("txnStatus").asText()).isEqualTo(TxnStatus.SUCCESS.name());
        assertThat(txn.get("data").get("successAt") == null || txn.get("data").get("successAt").isNull()).isFalse();
        // 订单已入账
        assertThat(orderByShortCode(code).getStatus()).isEqualTo(OrderStatus.SUCCESS.name());
    }

    @Test
    void duplicateSuccessCallbackShouldBeIdempotent() throws Exception {
        String code = insertOrder("P3", OrderStatus.PENDING.name(), LocalDateTime.now().plusHours(1));
        String txnNo = pay(code, null).get("data").get("txnNo").asText();

        // 同一成功报文（相同渠道交易号）投递两次
        MockNotifyMessage msg = signedMsg(txnNo, "MKDUP0001", 9900L, "SUCCESS", null);
        assertThat(postJson("/api/pay/notify/mock", msg).get("code").asInt()).isZero();
        assertThat(postJson("/api/pay/notify/mock", msg).get("code").asInt()).isZero();

        // 仅一次入账：订单已支付、渠道交易号唯一
        assertThat(orderByShortCode(code).getStatus()).isEqualTo(OrderStatus.SUCCESS.name());
        Long count = paymentTxnMapper.selectCount(new LambdaQueryWrapper<PaymentTxn>()
                .eq(PaymentTxn::getChannelTxnNo, "MKDUP0001"));
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void forgedSignatureShouldBeRejectedWithoutTouchingData() throws Exception {
        String code = insertOrder("P4", OrderStatus.PENDING.name(), LocalDateTime.now().plusHours(1));
        String txnNo = pay(code, null).get("data").get("txnNo").asText();

        MockNotifyMessage forged = signedMsg(txnNo, "MKFORGE0001", 9900L, "SUCCESS", null);
        forged.setSign("deadbeef"); // 伪造签名
        JsonNode resp = postJson("/api/pay/notify/mock", forged);
        assertThat(resp.get("code").asInt()).isEqualTo(23001);

        // 数据不变
        assertThat(orderByShortCode(code).getStatus()).isEqualTo(OrderStatus.PENDING.name());
        PaymentTxn txn = paymentTxnMapper.selectOne(new LambdaQueryWrapper<PaymentTxn>()
                .eq(PaymentTxn::getTxnNo, txnNo));
        assertThat(txn.getStatus()).isEqualTo(TxnStatus.CREATED.name());
        assertThat(txn.getChannelTxnNo()).isNull();
    }

    @Test
    void amountMismatchShouldBeRejected() throws Exception {
        String code = insertOrder("P5", OrderStatus.PENDING.name(), LocalDateTime.now().plusHours(1));
        String txnNo = pay(code, null).get("data").get("txnNo").asText();

        // 签名合法但金额不一致（amountCent=1）
        MockNotifyMessage bad = signedMsg(txnNo, "MKAMT0001", 1L, "SUCCESS", null);
        JsonNode resp = postJson("/api/pay/notify/mock", bad);
        assertThat(resp.get("code").asInt()).isEqualTo(23002);

        assertThat(orderByShortCode(code).getStatus()).isEqualTo(OrderStatus.PENDING.name());
        assertThat(requireTxn(txnNo).getStatus()).isEqualTo(TxnStatus.CREATED.name());
    }

    @Test
    void failedCallbackShouldKeepOrderPendingAndRetryCreatesNewTxn() throws Exception {
        String code = insertOrder("P6", OrderStatus.PENDING.name(), LocalDateTime.now().plusHours(1));
        String firstTxnNo = pay(code, null).get("data").get("txnNo").asText();

        // 模拟失败：流水置失败、订单保持待支付
        JsonNode accepted = postJson("/api/mock/channel/pay", Map.of("txnNo", firstTxnNo, "result", "FAILED"));
        assertThat(accepted.get("code").asInt()).isZero();
        PaymentTxn failedTxn = requireTxn(firstTxnNo);
        assertThat(failedTxn.getStatus()).isEqualTo(TxnStatus.FAILED.name());
        assertThat(failedTxn.getFailReason()).isNotBlank();
        assertThat(orderByShortCode(code).getStatus()).isEqualTo(OrderStatus.PENDING.name());

        // 重新支付：生成新流水，不复用失败流水
        String secondTxnNo = pay(code, null).get("data").get("txnNo").asText();
        assertThat(secondTxnNo).isNotEqualTo(firstTxnNo);
        assertThat(requireTxn(secondTxnNo).getStatus()).isEqualTo(TxnStatus.CREATED.name());
        assertThat(paymentTxnMapper.selectCount(new LambdaQueryWrapper<PaymentTxn>()
                .eq(PaymentTxn::getOrderId, failedTxn.getOrderId()))).isEqualTo(2L);
    }

    @Test
    void nonPendingOrderShouldBeRejectedOnPay() throws Exception {
        String closed = insertOrder("P7", OrderStatus.CLOSED.name(), LocalDateTime.now().plusHours(1));
        assertThat(pay(closed, null).get("code").asInt()).isEqualTo(21001); // 短链/订单已失效

        String success = insertOrder("P8", OrderStatus.SUCCESS.name(), LocalDateTime.now().plusHours(1));
        assertThat(pay(success, null).get("code").asInt()).isEqualTo(20003); // 已支付不可重复支付
    }

    @Test
    void unknownTxnShouldReturnNotFound() throws Exception {
        assertThat(getJson("/api/open/txns/TXNOTEXIST000000").get("code").asInt()).isEqualTo(22001);
        assertThat(postJson("/api/mock/channel/pay",
                Map.of("txnNo", "TXNOTEXIST000000", "result", "SUCCESS")).get("code").asInt()).isEqualTo(22001);
    }

    private PaymentTxn requireTxn(String txnNo) {
        PaymentTxn txn = paymentTxnMapper.selectOne(new LambdaQueryWrapper<PaymentTxn>()
                .eq(PaymentTxn::getTxnNo, txnNo));
        assertThat(txn).isNotNull();
        return txn;
    }
}
