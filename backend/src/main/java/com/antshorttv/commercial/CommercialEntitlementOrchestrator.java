package com.antshorttv.commercial;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommercialEntitlementOrchestrator {
    private final CommercialOrderMapper orderMapper;
    private final CommercialPaymentMapper paymentMapper;
    private final CommercialFulfillmentRunner fulfillmentRunner;
    private final CommercialBenefitFulfillmentService benefits;

    public CommercialEntitlementOrchestrator(CommercialOrderMapper orderMapper, CommercialPaymentMapper paymentMapper,
        CommercialFulfillmentRunner fulfillmentRunner, CommercialBenefitFulfillmentService benefits) {
        this.orderMapper=orderMapper; this.paymentMapper=paymentMapper;
        this.fulfillmentRunner=fulfillmentRunner; this.benefits=benefits;
    }

    @Transactional
    public CommercialOrderEntity confirmPaid(Long orderId, String providerTradeNo, BigDecimal paidAmount, LocalDateTime paidAt) {
        CommercialOrderEntity order = orderMapper.selectOne(new QueryWrapper<CommercialOrderEntity>().eq("id", orderId).last("for update")); if (order == null) throw new IllegalArgumentException("Order not found");
        if ("COMPLETED".equals(order.status)) return order;
        if (!List.of("PENDING_PAYMENT", "PAYMENT_EXCEPTION", "ENTITLEMENT_PENDING").contains(order.status)) throw new IllegalStateException("Order cannot be paid from " + order.status);
        if (paidAmount == null || order.amount.compareTo(paidAmount) != 0) throw new IllegalArgumentException("Payment amount mismatch");
        CommercialPaymentEntity payment = paymentMapper.selectOne(new QueryWrapper<CommercialPaymentEntity>().eq("order_id", order.id));
        payment.providerTradeNo = providerTradeNo; payment.status = "PAID"; payment.paidAt = paidAt; payment.updatedAt = LocalDateTime.now(); paymentMapper.updateById(payment);
        order.status = "ENTITLEMENT_PENDING"; order.paidAt = paidAt; order.updatedAt = LocalDateTime.now(); orderMapper.updateById(order);
        try {
            fulfillmentRunner.run(() -> benefits.fulfill(new CommercialBenefitFulfillmentService.Source(
                order.tenantId, order.userId, order.packageVersionId, order.id, null, order.packageSnapshotJson, order.paidAt)));
            order.status = "COMPLETED"; order.completedAt = LocalDateTime.now(); order.updatedAt = order.completedAt; orderMapper.updateById(order);
        } catch (RuntimeException exception) {
            order.status = "ENTITLEMENT_PENDING"; order.updatedAt = LocalDateTime.now(); orderMapper.updateById(order);
        }
        return order;
    }

}
