package com.payment.diff.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.payment.diff.common.exception.BusinessException;
import com.payment.diff.common.exception.ErrorCode;
import com.payment.diff.link.ShortCodeGenerator;
import com.payment.diff.payment.PaymentTxn;
import com.payment.diff.payment.PaymentTxnMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 补差价单领域服务：创建、状态机流转（显式校验 + 条件 UPDATE 兜底）、超时关单、查询。
 * 状态机红线：仅 PENDING 可流转；流转收敛在本服务内。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiffOrderService {

    private static final int SHORT_CODE_MAX_RETRY = 5;

    private final DiffOrderMapper diffOrderMapper;
    private final PaymentTxnMapper paymentTxnMapper;

    @Value("${diffpay.link.base-url}")
    private String shortLinkBaseUrl;

    // ---------- 创建 ----------

    @Transactional
    public CreateOrderResponse create(CreateOrderRequest request, String operator) {
        int expireHours = request.resolveExpireHours();

        DiffOrder order = new DiffOrder();
        order.setOrderNo("TMP" + UUID.randomUUID().toString().replace("-", ""));
        order.setOriginalOrderNo(request.originalOrderNo());
        order.setOriginalAmountCent(request.originalAmountCent());
        order.setOriginalProduct(request.originalProduct());
        order.setProductName(request.productName());
        order.setQuotaDesc(request.quotaDesc());
        order.setRemark(request.remark());
        order.setAmountCent(request.amountCent());
        order.setStatus(OrderStatus.PENDING.name());
        order.setExpireAt(LocalDateTime.now().plusHours(expireHours));
        order.setCreatedBy(operator);

        // 短码唯一索引冲突时重试（SecureRandom 8 位 Base62，碰撞概率可忽略）
        for (int attempt = 1; ; attempt++) {
            order.setShortCode(ShortCodeGenerator.next());
            try {
                diffOrderMapper.insert(order);
                break;
            } catch (DuplicateKeyException e) {
                if (attempt >= SHORT_CODE_MAX_RETRY) {
                    throw e;
                }
            }
        }

        // 订单号：BJ + yyyyMMdd + 6位序列（序列取自自增 id）
        order.setOrderNo(BusinessNoFormatter.format("BJ", order.getId()));
        DiffOrder patch = new DiffOrder();
        patch.setId(order.getId());
        patch.setOrderNo(order.getOrderNo());
        diffOrderMapper.updateById(patch);

        String shortUrl = shortLinkBaseUrl + "/" + order.getShortCode();
        return new CreateOrderResponse(order.getOrderNo(), order.getShortCode(), shortUrl,
                order.getStatus(), order.getExpireAt());
    }

    // ---------- 状态机流转（仅 PENDING 可流转） ----------

    /**
     * 支付成功入账：显式校验（未超时 + PENDING）→ 条件 UPDATE（WHERE status='PENDING'）。
     * 返回 true = 本次调用完成入账；false = 并发竞争失败（幂等路径，入账已由其他请求完成）。
     */
    public boolean markSuccess(long orderId) {
        DiffOrder order = requireOrder(orderId);
        LocalDateTime now = LocalDateTime.now();
        // 惰性校验：超时订单即使未被定时任务关闭，也拒绝入账并关单（双保险）
        if (order.isExpired(now)) {
            closeInternal(order.getId());
            throw new BusinessException(ErrorCode.ORDER_EXPIRED);
        }
        OrderStatus current = OrderStatus.valueOf(order.getStatus());
        if (current == OrderStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单已支付，不可重复入账");
        }
        if (current == OrderStatus.CLOSED) {
            throw new BusinessException(ErrorCode.ORDER_CLOSED, "订单已关闭，不可入账");
        }
        int updated = diffOrderMapper.update(null, new LambdaUpdateWrapper<DiffOrder>()
                .eq(DiffOrder::getId, orderId)
                .eq(DiffOrder::getStatus, OrderStatus.PENDING.name())
                .set(DiffOrder::getStatus, OrderStatus.SUCCESS.name())
                .set(DiffOrder::getPaidAt, now));
        if (updated == 0) {
            // 并发竞争：另一请求已完成入账，走幂等路径
            log.warn("订单入账条件更新未命中（并发竞争/状态已变化）, orderId={}", orderId);
            return false;
        }
        return true;
    }

    /** 手动关单：仅 PENDING 可关 */
    public void closeByOrderNo(String orderNo, String operator) {
        DiffOrder order = requireOrderByNo(orderNo);
        OrderStatus current = OrderStatus.valueOf(order.getStatus());
        if (current == OrderStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "已支付订单不可关闭");
        }
        if (current == OrderStatus.CLOSED) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单已关闭，无需重复操作");
        }
        boolean closed = closeInternal(order.getId());
        if (!closed) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "订单状态已变化，请刷新后重试");
        }
        log.info("订单已手动关闭, orderNo={}, operator={}", orderNo, operator);
    }

    /** 条件 UPDATE 关单：仅当仍为 PENDING 时生效 */
    private boolean closeInternal(long orderId) {
        int updated = diffOrderMapper.update(null, new LambdaUpdateWrapper<DiffOrder>()
                .eq(DiffOrder::getId, orderId)
                .eq(DiffOrder::getStatus, OrderStatus.PENDING.name())
                .set(DiffOrder::getStatus, OrderStatus.CLOSED.name()));
        return updated > 0;
    }

    // ---------- 超时关单（定时扫描 + 惰性校验双保险中的定时侧） ----------

    /** 每分钟扫描过期 PENDING 订单并关闭，返回本次关闭数量（供测试断言） */
    public int closeExpiredOrders() {
        List<DiffOrder> expired = diffOrderMapper.selectList(new LambdaQueryWrapper<DiffOrder>()
                .eq(DiffOrder::getStatus, OrderStatus.PENDING.name())
                .lt(DiffOrder::getExpireAt, LocalDateTime.now())
                .last("LIMIT 200"));
        int closed = 0;
        for (DiffOrder order : expired) {
            if (closeInternal(order.getId())) {
                closed++;
            }
        }
        if (closed > 0) {
            log.info("定时关单完成, closed={}", closed);
        }
        return closed;
    }

    // ---------- 查询 ----------

    public DiffOrder requireOrderByNo(String orderNo) {
        DiffOrder order = diffOrderMapper.selectOne(new LambdaQueryWrapper<DiffOrder>()
                .eq(DiffOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    public Page<DiffOrder> page(long page, long size, String status, String keyword,
                                String originalOrderNo, LocalDateTime beginTime, LocalDateTime endTime) {
        LambdaQueryWrapper<DiffOrder> wrapper = new LambdaQueryWrapper<DiffOrder>()
                .eq(StringUtils.hasText(status), DiffOrder::getStatus, status)
                .eq(StringUtils.hasText(originalOrderNo), DiffOrder::getOriginalOrderNo, originalOrderNo)
                .ge(beginTime != null, DiffOrder::getCreatedAt, beginTime)
                .le(endTime != null, DiffOrder::getCreatedAt, endTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(DiffOrder::getOrderNo, keyword)
                    .or().like(DiffOrder::getOriginalOrderNo, keyword)
                    .or().like(DiffOrder::getProductName, keyword));
        }
        wrapper.orderByDesc(DiffOrder::getId);
        return diffOrderMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public OrderDetailResponse detail(String orderNo) {
        DiffOrder order = requireOrderByNo(orderNo);
        List<PaymentTxn> txns = paymentTxnMapper.selectList(new LambdaQueryWrapper<PaymentTxn>()
                .eq(PaymentTxn::getOrderId, order.getId())
                .orderByDesc(PaymentTxn::getId));
        OrderDetailResponse response = new OrderDetailResponse();
        response.setOrder(order);
        response.setOriginalOrderNo(order.getOriginalOrderNo());
        response.setOriginalAmountCent(order.getOriginalAmountCent());
        response.setOriginalProduct(order.getOriginalProduct());
        response.setTxns(txns);
        return response;
    }

    private DiffOrder requireOrder(long orderId) {
        DiffOrder order = diffOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }
}
