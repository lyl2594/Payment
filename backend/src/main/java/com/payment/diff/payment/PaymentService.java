package com.payment.diff.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.payment.diff.common.api.ApiResponse;
import com.payment.diff.common.exception.BusinessException;
import com.payment.diff.common.exception.ErrorCode;
import com.payment.diff.link.LinkService;
import com.payment.diff.order.BusinessNoFormatter;
import com.payment.diff.order.DiffOrder;
import com.payment.diff.order.DiffOrderMapper;
import com.payment.diff.order.DiffOrderService;
import com.payment.diff.payment.gateway.MockNotifyMessage;
import com.payment.diff.payment.gateway.PaymentGateway;
import com.payment.diff.payment.gateway.PrepayResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 支付领域服务：发起支付、流水查询、回调处理（三道防线）。
 * 红线：回调必须依次通过 验签 → 状态机前置校验 → 金额一致性 → 条件 UPDATE；
 * 渠道交易号唯一约束兜底；重复成功回调返回受理成功且不重复入账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTxnMapper paymentTxnMapper;
    private final DiffOrderMapper diffOrderMapper;
    private final DiffOrderService diffOrderService;
    private final LinkService linkService;
    private final PaymentGateway paymentGateway;

    // ---------- 发起支付 ----------

    /**
     * 发起支付：校验订单可用 → 创建 CREATED 流水 → 网关预下单返回收银台地址。
     * 金额权威：一律取订单服务端存储值，客户端提交的任何金额参数都被忽略。
     */
    @Transactional
    public PayCreateResponse createPayment(String shortCode) {
        DiffOrder order = linkService.requireValidOrderByShortCode(shortCode);
        if (!order.isPending()) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单已支付，无需重复支付");
        }

        PaymentTxn txn = new PaymentTxn();
        txn.setTxnNo("TMP" + UUID.randomUUID().toString().replace("-", ""));
        txn.setOrderId(order.getId());
        txn.setAmountCent(order.getAmountCent());
        txn.setStatus(TxnStatus.CREATED.name());
        txn.setChannel(paymentGateway.channel());
        paymentTxnMapper.insert(txn);

        // 流水号：TX + yyyyMMdd + 6位序列（序列取自自增 id）
        PaymentTxn patch = new PaymentTxn();
        patch.setId(txn.getId());
        patch.setTxnNo(BusinessNoFormatter.format("TX", txn.getId()));
        paymentTxnMapper.updateById(patch);
        txn.setTxnNo(patch.getTxnNo());

        PrepayResult prepay = paymentGateway.prepay(txn);
        // 收银台地址附加 shortCode：模拟第三方完成支付后凭其跳回商户 H5 结果页
        String cashierUrl = prepay.cashierUrl() + "?shortCode=" + order.getShortCode();
        log.info("发起支付, orderNo={}, txnNo={}, amountCent={}", order.getOrderNo(), txn.getTxnNo(), txn.getAmountCent());
        return new PayCreateResponse(txn.getTxnNo(), txn.getStatus(), txn.getAmountCent(),
                order.getOrderNo(), cashierUrl);
    }

    // ---------- 流水查询 ----------

    /** 流水状态查询（H5 轮询）：含终态、失败原因与关联订单状态 */
    public TxnStatusResponse getTxnStatus(String txnNo) {
        PaymentTxn txn = requireTxn(txnNo);
        DiffOrder order = diffOrderMapper.selectById(txn.getOrderId());
        return new TxnStatusResponse(txn.getTxnNo(), txn.getStatus(), txn.getAmountCent(),
                txn.getFailReason(), txn.getSuccessAt(),
                order == null ? null : order.getOrderNo(),
                order == null ? null : order.getStatus());
    }

    public PaymentTxn requireTxn(String txnNo) {
        PaymentTxn txn = paymentTxnMapper.selectOne(new LambdaQueryWrapper<PaymentTxn>()
                .eq(PaymentTxn::getTxnNo, txnNo));
        if (txn == null) {
            throw new BusinessException(ErrorCode.TXN_NOT_FOUND);
        }
        return txn;
    }

    // ---------- 回调处理（三道防线） ----------

    @Transactional
    public ApiResponse<Void> handleNotify(MockNotifyMessage raw) {
        // 防线一：验签。失败直接拒绝，不触碰任何数据
        MockNotifyMessage msg = paymentGateway.verifyAndParse(raw);
        PaymentTxn txn = requireTxn(msg.getTxnNo());
        boolean successNotify = MockNotifyMessage.RESULT_SUCCESS.equals(msg.getPayResult());

        // 状态机前置校验：仅 CREATED 流水可被更新为终态
        if (!TxnStatus.CREATED.name().equals(txn.getStatus())) {
            if (successNotify && TxnStatus.SUCCESS.name().equals(txn.getStatus())
                    && msg.getChannelTxnNo() != null && msg.getChannelTxnNo().equals(txn.getChannelTxnNo())) {
                // 幂等受理：重复成功回调返回受理成功，不产生重复入账
                log.info("重复成功回调幂等受理, txnNo={}", txn.getTxnNo());
                return ApiResponse.ok();
            }
            return ApiResponse.fail(ErrorCode.TXN_STATE_INVALID.getCode(), "支付流水已终态，不可更新");
        }

        if (!successNotify) {
            // 失败回调：仅流水置失败，订单保持待支付以便重试
            paymentTxnMapper.update(null, new LambdaUpdateWrapper<PaymentTxn>()
                    .eq(PaymentTxn::getId, txn.getId())
                    .eq(PaymentTxn::getStatus, TxnStatus.CREATED.name())
                    .set(PaymentTxn::getStatus, TxnStatus.FAILED.name())
                    .set(PaymentTxn::getFailReason,
                            StringUtils.hasText(msg.getFailReason()) ? msg.getFailReason() : "UNKNOWN"));
            log.info("回调流水置失败, txnNo={}, reason={}", txn.getTxnNo(), msg.getFailReason());
            return ApiResponse.ok();
        }

        // 金额一致性：成功回调金额必须与流水应付金额一致
        if (msg.getAmountCent() == null || !msg.getAmountCent().equals(txn.getAmountCent())) {
            log.error("[金额一致性异常] 回调金额与流水不符, txnNo={}, 应付={}, 来报={}",
                    txn.getTxnNo(), txn.getAmountCent(), msg.getAmountCent());
            return ApiResponse.fail(ErrorCode.AMOUNT_MISMATCH.getCode(), ErrorCode.AMOUNT_MISMATCH.getMessage());
        }

        // 条件 UPDATE 回写流水（WHERE status='CREATED'；channel_txn_no 唯一约束兜底）
        int updated;
        try {
            updated = paymentTxnMapper.update(null, new LambdaUpdateWrapper<PaymentTxn>()
                    .eq(PaymentTxn::getId, txn.getId())
                    .eq(PaymentTxn::getStatus, TxnStatus.CREATED.name())
                    .set(PaymentTxn::getStatus, TxnStatus.SUCCESS.name())
                    .set(PaymentTxn::getChannelTxnNo, msg.getChannelTxnNo())
                    .set(PaymentTxn::getSuccessAt, LocalDateTime.now()));
        } catch (DuplicateKeyException e) {
            // 防线二：channel_txn_no 唯一约束兜底（跨流水重复渠道交易号，按已受理幂等处理）
            log.warn("渠道交易号唯一约束冲突，按已受理幂等处理, txnNo={}, channelTxnNo={}",
                    txn.getTxnNo(), msg.getChannelTxnNo());
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.ok();
        }
        if (updated == 0) {
            // 并发竞争：重查后按幂等或状态非法处理
            PaymentTxn latest = requireTxn(msg.getTxnNo());
            if (TxnStatus.SUCCESS.name().equals(latest.getStatus())
                    && msg.getChannelTxnNo().equals(latest.getChannelTxnNo())) {
                return ApiResponse.ok();
            }
            return ApiResponse.fail(ErrorCode.TXN_STATE_INVALID.getCode(), ErrorCode.TXN_STATE_INVALID.getMessage());
        }

        // 订单入账（状态机收敛在订单领域服务，含超时惰性校验双保险）
        try {
            diffOrderService.markSuccess(txn.getOrderId());
        } catch (BusinessException e) {
            // 入账被拒（订单已过期/已关闭）：流水回置失败留痕，应答失败（渠道重试将持续被拒）
            log.warn("回调入账被拒, txnNo={}, orderId={}, reason={}", txn.getTxnNo(), txn.getOrderId(), e.getMessage());
            revertTxnToFailed(txn.getId(), "入账被拒: " + e.getMessage());
            return ApiResponse.fail(e.getErrorCode().getCode(), e.getErrorCode().getMessage());
        }
        log.info("回调入账成功, txnNo={}, channelTxnNo={}", txn.getTxnNo(), msg.getChannelTxnNo());
        return ApiResponse.ok();
    }

    /** 入账被拒时回置流水为失败（同事务内，本行归属当前事务） */
    private void revertTxnToFailed(long txnId, String reason) {
        paymentTxnMapper.update(null, new LambdaUpdateWrapper<PaymentTxn>()
                .eq(PaymentTxn::getId, txnId)
                .set(PaymentTxn::getStatus, TxnStatus.FAILED.name())
                .set(PaymentTxn::getFailReason, reason));
    }
}
