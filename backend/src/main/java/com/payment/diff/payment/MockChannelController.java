package com.payment.diff.payment;

import com.payment.diff.common.api.ApiResponse;
import com.payment.diff.common.exception.BusinessException;
import com.payment.diff.common.exception.ErrorCode;
import com.payment.diff.payment.gateway.MockNotifyMessage;
import com.payment.diff.payment.gateway.MockSigner;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 模拟支付渠道端点（渠道侧视角）：接收模拟收银台的用户操作，
 * 由"渠道"生成渠道交易号、组装回调报文并签名，再 HTTP 回环调用商户回调端点，
 * 模拟真实第三方渠道的异步通知。商户侧仅凭验签信任回调。
 */
@Slf4j
@RestController
@RequestMapping("/api/mock/channel")
@RequiredArgsConstructor
public class MockChannelController {

    private final PaymentService paymentService;
    private final LoopbackNotifySender notifySender;

    @Value("${diffpay.pay.mock-channel-secret}")
    private String channelSecret;

    @PostMapping("/pay")
    public ApiResponse<Void> pay(@RequestBody @Valid MockPayRequest request) {
        if (!MockNotifyMessage.RESULT_SUCCESS.equals(request.result())
                && !MockNotifyMessage.RESULT_FAILED.equals(request.result())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "result 仅支持 SUCCESS/FAILED");
        }
        PaymentTxn txn = paymentService.requireTxn(request.txnNo());

        // 渠道侧：生成渠道交易号并签名（金额取自流水记录，与商户侧一致）
        MockNotifyMessage message = new MockNotifyMessage();
        message.setTxnNo(txn.getTxnNo());
        message.setChannelTxnNo("MK" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        message.setAmountCent(txn.getAmountCent());
        message.setPayResult(request.result());
        message.setFailReason(MockNotifyMessage.RESULT_SUCCESS.equals(request.result()) ? null : "MOCK_PAY_USER_FAIL");
        message.setTimestamp(System.currentTimeMillis());
        message.setSign(MockSigner.sign(message, channelSecret));

        // HTTP 回环投递异步通知，返回受理结果
        ApiResponse<Void> accepted = notifySender.send(message);
        log.info("模拟渠道已投递回调, txnNo={}, result={}, 受理code={}", txn.getTxnNo(), request.result(), accepted.getCode());
        return accepted;
    }
}
