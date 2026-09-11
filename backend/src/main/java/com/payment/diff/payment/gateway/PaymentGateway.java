package com.payment.diff.payment.gateway;

import com.payment.diff.payment.PaymentTxn;

/**
 * 统一支付网关抽象：渠道相关逻辑只允许出现在本接口及其实现（一期 MockGateway）。
 * 订单/支付业务仅依赖本接口，替换渠道时提供新实现即可，不改变业务流程。
 * <p>
 * 契约与真实渠道（微信/支付宝）同构：预下单 → 收银台 → 异步回调 → 验签解析。
 * 各渠道自有报文类型由实现定义（一期统一为 {@link MockNotifyMessage}）。
 */
public interface PaymentGateway {

    /** 渠道标识（如 MOCK） */
    String channel();

    /** 预下单：输入支付流水，输出收银台跳转地址 */
    PrepayResult prepay(PaymentTxn txn);

    /** 回调验签与解析：验签失败抛 SIGN_INVALID，不触碰任何数据；成功返回规范化报文 */
    MockNotifyMessage verifyAndParse(MockNotifyMessage raw);
}
