package com.antshorttv.commercial;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class WechatPaymentNotificationService {
    private final WechatPayNotificationVerifier verifier;
    private final CommercialOrderMapper orderMapper;
    private final CommercialEntitlementOrchestrator orchestrator;
    private final CommercialPaymentMapper paymentMapper;
    private final CommercialPaymentEventMapper eventMapper;
    private final CommercialAuditMapper auditMapper;

    public WechatPaymentNotificationService(
        WechatPayNotificationVerifier verifier,
        CommercialOrderMapper orderMapper,
        CommercialEntitlementOrchestrator orchestrator,
        CommercialPaymentMapper paymentMapper,
        CommercialPaymentEventMapper eventMapper,
        CommercialAuditMapper auditMapper
    ) {
        this.verifier = verifier;
        this.orderMapper = orderMapper;
        this.orchestrator = orchestrator;
        this.paymentMapper = paymentMapper;
        this.eventMapper = eventMapper;
        this.auditMapper = auditMapper;
    }

    public void process(String timestamp, String nonce, String signature, String serial, String body) {
        WechatPaymentNotification notification = verifier.verify(timestamp, nonce, signature, serial, body);
        CommercialOrderEntity order = orderMapper.selectOne(new QueryWrapper<CommercialOrderEntity>()
            .eq("merchant_order_no", notification.merchantOrderNo()));
        if (order == null) throw new IllegalArgumentException("Merchant order number does not exist");
        CommercialPaymentEventEntity event = eventMapper.selectOne(new QueryWrapper<CommercialPaymentEventEntity>()
            .eq("provider", "WECHAT_NATIVE").eq("provider_event_id", notification.eventId()));
        if (event != null && Boolean.TRUE.equals(event.processed)) return;
        if (event == null) {
            event = new CommercialPaymentEventEntity();
            event.orderId = order.id; event.provider = "WECHAT_NATIVE"; event.eventType = "TRANSACTION.SUCCESS";
            event.providerEventId = notification.eventId(); event.payloadJson = notification.rawBody();
            event.processed = false; event.createdAt = LocalDateTime.now();
            try { eventMapper.insert(event); }
            catch (DuplicateKeyException duplicate) {
                event = eventMapper.selectOne(new QueryWrapper<CommercialPaymentEventEntity>()
                    .eq("provider", "WECHAT_NATIVE").eq("provider_event_id", notification.eventId()));
                if (event != null && Boolean.TRUE.equals(event.processed)) return;
                if (event == null) throw duplicate;
            }
        }
        try {
            if (!order.currency.equals(notification.currency())) throw new IllegalArgumentException("Payment currency mismatch");
            orchestrator.confirmPaid(order.id, notification.providerTradeNo(), notification.amount(), notification.paidAt());
            event.processed = true;
            eventMapper.updateById(event);
            String operation = "COMPLETED".equals(order.status) ? "PAYMENT_CALLBACK_CONFIRMED" : "ENTITLEMENT_PENDING";
            audit(order, operation, "{\"eventId\":\"" + notification.eventId() + "\"}");
        } catch (RuntimeException exception) {
            markException(order, exception.getMessage());
            audit(order, "PAYMENT_EXCEPTION", "{\"eventId\":\"" + notification.eventId() + "\",\"reason\":\"" + escape(exception.getMessage()) + "\"}");
            throw exception;
        }
    }

    private void markException(CommercialOrderEntity order, String reason) {
        order.status = "PAYMENT_EXCEPTION"; order.updatedAt = LocalDateTime.now(); orderMapper.updateById(order);
        CommercialPaymentEntity payment = paymentMapper.selectOne(new QueryWrapper<CommercialPaymentEntity>().eq("order_id", order.id));
        payment.status = "EXCEPTION"; payment.rawResponseJson = "{\"error\":\"" + escape(reason) + "\"}";
        payment.updatedAt = order.updatedAt; paymentMapper.updateById(payment);
    }

    private void audit(CommercialOrderEntity order, String operation, String detail) {
        CommercialAuditEntity audit = new CommercialAuditEntity(); audit.tenantId = order.tenantId;
        audit.userId = order.userId; audit.operatorType = "WECHAT"; audit.operation = operation;
        audit.targetType = "COMMERCIAL_ORDER"; audit.targetId = order.id; audit.detailJson = detail;
        audit.createdAt = LocalDateTime.now(); auditMapper.insert(audit);
    }

    private String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
