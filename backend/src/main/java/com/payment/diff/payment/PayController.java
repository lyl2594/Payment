package com.payment.diff.payment;

import com.payment.diff.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * H5 支付开放接口（凭短码/流水号匿名访问）：
 * - POST /api/open/s/{shortCode}/pay 发起支付，返回收银台地址
 * - GET  /api/open/txns/{txnNo}      流水状态轮询
 */
@RestController
@RequestMapping("/api/open")
@RequiredArgsConstructor
public class PayController {

    private final PaymentService paymentService;

    /**
     * 发起支付。客户端仅提交订单标识（短码）：请求体中的任何金额参数一律忽略，
     * 支付金额由服务端依据订单数据确定（金额权威红线）。
     */
    @PostMapping("/s/{shortCode}/pay")
    public ApiResponse<PayCreateResponse> pay(@PathVariable String shortCode,
                                              @RequestBody(required = false) Map<String, Object> ignoredBody) {
        return ApiResponse.ok(paymentService.createPayment(shortCode));
    }

    /** 流水状态查询（供 H5 轮询至终态） */
    @GetMapping("/txns/{txnNo}")
    public ApiResponse<TxnStatusResponse> txnStatus(@PathVariable String txnNo) {
        return ApiResponse.ok(paymentService.getTxnStatus(txnNo));
    }
}
