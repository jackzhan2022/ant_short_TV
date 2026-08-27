package com.antshorttv.commercial;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.antshorttv.points.PointAccountingService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommercialEntitlementOrchestrator {
    private final CommercialOrderMapper orderMapper; private final CommercialPaymentMapper paymentMapper;
    private final CommercialPackageMapper packageMapper; private final CommercialPackageVersionMapper versionMapper;
    private final CommercialEntitlementMapper entitlementMapper; private final CommercialEntitlementGrantMapper grantMapper;
    private final TeamSubscriptionMapper subscriptionMapper; private final PointAccountingService accounting;
    private final CommercialFulfillmentRunner fulfillmentRunner;
    public CommercialEntitlementOrchestrator(CommercialOrderMapper o, CommercialPaymentMapper p, CommercialPackageMapper pm, CommercialPackageVersionMapper vm, CommercialEntitlementMapper em, CommercialEntitlementGrantMapper gm, TeamSubscriptionMapper sm, PointAccountingService accounting, CommercialFulfillmentRunner fulfillmentRunner) { this.orderMapper=o; this.paymentMapper=p; this.packageMapper=pm; this.versionMapper=vm; this.entitlementMapper=em; this.grantMapper=gm; this.subscriptionMapper=sm; this.accounting=accounting; this.fulfillmentRunner=fulfillmentRunner; }

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
            fulfillmentRunner.run(() -> fulfill(order));
            order.status = "COMPLETED"; order.completedAt = LocalDateTime.now(); order.updatedAt = order.completedAt; orderMapper.updateById(order);
        } catch (RuntimeException exception) {
            order.status = "ENTITLEMENT_PENDING"; order.updatedAt = LocalDateTime.now(); orderMapper.updateById(order);
        }
        return order;
    }

    private void fulfill(CommercialOrderEntity order) {
        CommercialPackageVersionEntity version = versionMapper.selectById(order.packageVersionId);
        CommercialPackageEntity pack = packageMapper.selectById(version.packageId);
        List<CommercialEntitlementEntity> entitlements = entitlementMapper.selectList(new QueryWrapper<CommercialEntitlementEntity>().eq("package_version_id", version.id));
        if ("POINT_PACKAGE".equals(pack.packageType)) entitlements.stream().filter(e -> "ONE_TIME_POINTS".equals(e.entitlementType)).forEach(e -> grantPoints(order, null, null, e.numericValue, "order:" + order.id + ":points"));
        else activateSubscription(order, version, entitlements);
    }

    private void activateSubscription(CommercialOrderEntity order, CommercialPackageVersionEntity version, List<CommercialEntitlementEntity> entitlements) {
        LocalDateTime now = order.paidAt;
        TeamSubscriptionEntity current = subscriptionMapper.selectOne(new QueryWrapper<TeamSubscriptionEntity>().eq("tenant_id", order.tenantId).eq("status", "ACTIVE").le("starts_at", now).gt("ends_at", now).orderByDesc("ends_at").last("limit 1"));
        if (current != null && current.packageVersionId.equals(version.id)) {
            current.endsAt = current.endsAt.plusMonths(version.periodMonths);
            current.updatedAt = LocalDateTime.now();
            subscriptionMapper.updateById(current);
            shiftQueuedSubscriptions(order.tenantId, version.periodMonths);
            return;
        }
        TeamSubscriptionEntity tail = subscriptionMapper.selectOne(new QueryWrapper<TeamSubscriptionEntity>()
            .eq("tenant_id", order.tenantId)
            .in("status", List.of("ACTIVE", "QUEUED"))
            .orderByDesc("ends_at")
            .last("limit 1"));
        TeamSubscriptionEntity sub = new TeamSubscriptionEntity(); sub.tenantId=order.tenantId; sub.packageVersionId=version.id; sub.sourceOrderId=order.id; sub.status=current == null ? "ACTIVE" : "QUEUED"; sub.startsAt=current == null ? now : tail.endsAt; sub.endsAt=sub.startsAt.plusMonths(version.periodMonths); sub.nextGrantAt=current == null ? sub.startsAt.plusMonths(1) : sub.startsAt; sub.snapshotJson=order.packageSnapshotJson; sub.createdAt=LocalDateTime.now(); sub.updatedAt=sub.createdAt; subscriptionMapper.insert(sub);
        if ("ACTIVE".equals(sub.status)) entitlements.stream().filter(e -> "PERIODIC_POINTS".equals(e.entitlementType)).forEach(e -> grantPoints(order, sub, 1, e.numericValue, "subscription:" + sub.id + ":period:1"));
    }

    private void shiftQueuedSubscriptions(Long tenantId, int months) {
        List<TeamSubscriptionEntity> queued = subscriptionMapper.selectList(new QueryWrapper<TeamSubscriptionEntity>()
            .eq("tenant_id", tenantId)
            .eq("status", "QUEUED")
            .orderByAsc("starts_at"));
        for (TeamSubscriptionEntity subscription : queued) {
            subscription.startsAt = subscription.startsAt.plusMonths(months);
            subscription.endsAt = subscription.endsAt.plusMonths(months);
            subscription.nextGrantAt = subscription.nextGrantAt.plusMonths(months);
            subscription.updatedAt = LocalDateTime.now();
            subscriptionMapper.updateById(subscription);
        }
    }
    private void grantPoints(CommercialOrderEntity order, TeamSubscriptionEntity sub, Integer periodNo, BigDecimal amount, String key) { CommercialEntitlementGrantEntity existing=grantMapper.selectOne(new QueryWrapper<CommercialEntitlementGrantEntity>().eq("tenant_id", order.tenantId).eq("idempotency_key", key)); if(existing!=null)return; accounting.grant(order.tenantId, order.userId, amount, key, "商业化权益发放"); CommercialEntitlementGrantEntity g=new CommercialEntitlementGrantEntity(); g.tenantId=order.tenantId; g.orderId=order.id; g.subscriptionId=sub==null?null:sub.id; g.periodNo=periodNo; g.entitlementType=sub==null?"ONE_TIME_POINTS":"PERIODIC_POINTS"; g.amount=amount; g.status="GRANTED"; g.idempotencyKey=key; g.grantedAt=LocalDateTime.now(); g.createdAt=g.grantedAt; g.updatedAt=g.grantedAt; grantMapper.insert(g); }
}
