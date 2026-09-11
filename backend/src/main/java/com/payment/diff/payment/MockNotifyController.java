package com.payment.diff.payment;

import com.payment.diff.common.api.ApiResponse;
import com.payment.diff.payment.gateway.MockNotifyMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道回调端点（模拟第三方异步通知入口，与真实渠道回调同构）。
 * 应答约定：code=0 表示受理成功（渠道停止重发）；非 0 表示受理失败（渠道将重试）。
 */
@RestController
@RequiredArgsConstructor
public class MockNotifyController {

    private final PaymentService paymentService;

    @PostMapping("/api/pay/notify/mock")
    public ApiResponse<Void> notify(@RequestBody MockNotifyMessage message) {
        return paymentService.handleNotify(message);
    }
}
