package com.payment.diff.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 模拟渠道签名工具：HMAC-SHA256。
 * 规范化串：txnNo|channelTxnNo|amountCent|payResult|timestamp（与真实渠道"参数拼接 + 密钥签名"同构）。
 */
public final class MockSigner {

    private MockSigner() {
    }

    public static String sign(MockNotifyMessage message, String secret) {
        try {
            String canonical = String.join("|",
                    n(message.getTxnNo()),
                    n(message.getChannelTxnNo()),
                    message.getAmountCent() == null ? "" : String.valueOf(message.getAmountCent()),
                    n(message.getPayResult()),
                    message.getTimestamp() == null ? "" : String.valueOf(message.getTimestamp()));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 签名计算失败", e);
        }
    }

    private static String n(String v) {
        return v == null ? "" : v;
    }
}
