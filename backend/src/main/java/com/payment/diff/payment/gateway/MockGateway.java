package com.payment.diff.payment.gateway;

import com.payment.diff.common.exception.BusinessException;
import com.payment.diff.common.exception.ErrorCode;
import com.payment.diff.payment.PaymentTxn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Mock 支付网关：与真实渠道流程同构的网关实现。
 * - prepay：生成模拟收银台地址（前端 /cashier/:txnNo）
 * - verifyAndParse：HMAC-SHA256 验签（常量时间比较），失败抛 SIGN_INVALID
 * 替换真实渠道时仅需提供新的 PaymentGateway 实现，订单/支付业务不感知渠道细节。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockGateway implements PaymentGateway {

    public static final String CHANNEL = "MOCK";

    @Value("${diffpay.pay.h5-base-url}")
    private String h5BaseUrl;

    @Value("${diffpay.pay.mock-channel-secret}")
    private String channelSecret;

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public PrepayResult prepay(PaymentTxn txn) {
        return new PrepayResult(h5BaseUrl + "/cashier/" + txn.getTxnNo());
    }

    @Override
    public MockNotifyMessage verifyAndParse(MockNotifyMessage raw) {
        String expected = MockSigner.sign(raw, channelSecret);
        if (raw.getSign() == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), raw.getSign().getBytes(StandardCharsets.UTF_8))) {
            log.warn("[回调验签失败] 拒绝处理, txnNo={}", raw.getTxnNo());
            throw new BusinessException(ErrorCode.SIGN_INVALID);
        }
        return raw;
    }
}
