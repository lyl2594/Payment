package com.payment.diff.payment;

import com.payment.diff.common.api.ApiResponse;
import com.payment.diff.common.exception.BusinessException;
import com.payment.diff.common.exception.ErrorCode;
import com.payment.diff.payment.gateway.MockNotifyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 回调投递器：以 HTTP 回环调用本服务回调端点，模拟第三方渠道异步通知。
 * 与进程内直调相比，回环调用保证回调路径（报文、验签、应答）与真实渠道完全同构。
 * <p>
 * 目标地址解析：存在 local.server.port（RANDOM_PORT 测试环境）优先使用；
 * 否则使用配置 diffpay.pay.self-base-url（生产/本地默认 8080）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoopbackNotifySender {

    private final Environment environment;

    /** 回环调用商户回调端点，返回受理结果（code=0 受理成功） */
    public ApiResponse<Void> send(MockNotifyMessage message) {
        String base = resolveBase();
        try {
            ApiResponse<Void> resp = RestClient.create().post()
                    .uri(base + "/api/pay/notify/mock")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (resp == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "回调受理应答为空");
            }
            return resp;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "回调投递失败: " + e.getMessage());
        }
    }

    private String resolveBase() {
        String port = environment.getProperty("local.server.port");
        if (port != null) {
            return "http://localhost:" + port;
        }
        return environment.getProperty("diffpay.pay.self-base-url", "http://localhost:8080");
    }
}
