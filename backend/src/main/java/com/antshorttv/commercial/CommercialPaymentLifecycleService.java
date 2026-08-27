package com.antshorttv.commercial;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CommercialPaymentLifecycleService {
    private final CommercialOrderMapper orderMapper;
    private final CommercialPaymentMapper paymentMapper;
    private final CommercialEntitlementOrchestrator orchestrator;
    private final WechatPayClient wechatPayClient;
    private final CommercialAuditMapper auditMapper;

    public CommercialPaymentLifecycleService(
        CommercialOrderMapper orderMapper,
        CommercialPaymentMapper paymentMapper,
        CommercialEntitlementOrchestrator orchestrator,
        WechatPayClient wechatPayClient,
        CommercialAuditMapper auditMapper
    ) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.orchestrator = orchestrator;
        this.wechatPayClient = wechatPayClient;
        this.auditMapper = auditMapper;
    }

    public CommercialOrderEntity refresh(Long orderId) {
        CommercialOrderEntity order = require(orderId);
        if (!List.of("PENDING_PAYMENT", "PAYMENT_EXCEPTION").contains(order.status)) return order;
        WechatPaymentStatus provider = wechatPayClient.queryOrder(order.merchantOrderNo);
        persistProviderEvidence(order, provider);
        audit(order, "PAYMENT_LOOKUP", "{\"providerStatus\":\"" + provider.status() + "\"}");
        if ("SUCCESS".equals(provider.status())) {
            if (provider.amount() == null) throw new IllegalArgumentException("Payment amount is missing");
            return orchestrator.confirmPaid(order.id, provider.providerTradeNo(), provider.amount(), provider.paidAt());
        }
        if ("CLOSED".equals(provider.status()) || "REVOKED".equals(provider.status())) markClosed(order);
        return order;
    }

    public int closeExpired(LocalDateTime now) {
        List<CommercialOrderEntity> expired = orderMapper.selectList(new QueryWrapper<CommercialOrderEntity>()
            .eq("status", "PENDING_PAYMENT").le("expires_at", now).orderByAsc("expires_at").last("limit 100"));
        int closed = 0;
        for (CommercialOrderEntity order : expired) {
            CommercialOrderEntity refreshed = refresh(order.id);
            if (!"PENDING_PAYMENT".equals(refreshed.status)) continue;
            wechatPayClient.closeOrder(order.merchantOrderNo);
            markClosed(order);
            audit(order, "PAYMENT_CLOSED", "{\"reason\":\"EXPIRED\"}");
            closed++;
        }
        return closed;
    }

    public int retryPendingEntitlements() {
        List<CommercialOrderEntity> pending = orderMapper.selectList(new QueryWrapper<CommercialOrderEntity>()
            .eq("status", "ENTITLEMENT_PENDING").orderByAsc("updated_at").last("limit 100"));
        int completed = 0;
        for (CommercialOrderEntity order : pending) {
            CommercialPaymentEntity payment = paymentMapper.selectOne(new QueryWrapper<CommercialPaymentEntity>().eq("order_id", order.id));
            CommercialOrderEntity result = orchestrator.confirmPaid(order.id, payment.providerTradeNo, payment.amount, payment.paidAt);
            if ("COMPLETED".equals(result.status)) completed++;
        }
        return completed;
    }

    public CommercialOrderEntity reconcile(Long orderId) {
        CommercialOrderEntity order = refresh(orderId);
        audit(order, "OPERATOR_RECONCILIATION", "{\"status\":\"" + order.status + "\"}");
        return order;
    }

    private void persistProviderEvidence(CommercialOrderEntity order, WechatPaymentStatus provider) {
        CommercialPaymentEntity payment = paymentMapper.selectOne(new QueryWrapper<CommercialPaymentEntity>().eq("order_id", order.id));
        payment.rawResponseJson = provider.rawResponse();
        payment.providerTradeNo = provider.providerTradeNo();
        payment.updatedAt = LocalDateTime.now();
        paymentMapper.updateById(payment);
    }

    private void markClosed(CommercialOrderEntity order) {
        order.status = "CLOSED";
        order.updatedAt = LocalDateTime.now();
        orderMapper.updateById(order);
        CommercialPaymentEntity payment = paymentMapper.selectOne(new QueryWrapper<CommercialPaymentEntity>().eq("order_id", order.id));
        payment.status = "CLOSED";
        payment.updatedAt = order.updatedAt;
        paymentMapper.updateById(payment);
    }

    private CommercialOrderEntity require(Long orderId) {
        CommercialOrderEntity order = orderMapper.selectById(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found");
        return order;
    }

    private void audit(CommercialOrderEntity order, String operation, String detail) {
        CommercialAuditEntity audit = new CommercialAuditEntity(); audit.tenantId = order.tenantId;
        audit.operatorType = "SYSTEM"; audit.operation = operation; audit.targetType = "COMMERCIAL_ORDER";
        audit.targetId = order.id; audit.detailJson = detail; audit.createdAt = LocalDateTime.now(); auditMapper.insert(audit);
    }
}
