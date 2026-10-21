package com.antshorttv.commercial;

import com.antshorttv.points.PointAccountingService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommercialSubscriptionGrantService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommercialSubscriptionGrantService.class);

    private final TeamSubscriptionMapper subscriptionMapper;
    private final CommercialEntitlementMapper entitlementMapper;
    private final CommercialEntitlementGrantMapper grantMapper;
    private final CommercialOrderMapper orderMapper;
    private final PointAccountingService accounting;
    private final AdminTenantGrantMapper adminGrants;

    public CommercialSubscriptionGrantService(
        TeamSubscriptionMapper subscriptionMapper,
        CommercialEntitlementMapper entitlementMapper,
        CommercialEntitlementGrantMapper grantMapper,
        CommercialOrderMapper orderMapper,
        PointAccountingService accounting,
        AdminTenantGrantMapper adminGrants
    ) {
        this.subscriptionMapper = subscriptionMapper;
        this.entitlementMapper = entitlementMapper;
        this.grantMapper = grantMapper;
        this.orderMapper = orderMapper;
        this.accounting = accounting;
        this.adminGrants = adminGrants;
    }

    public void processDue(LocalDateTime now) {
        List<TeamSubscriptionEntity> active = subscriptionMapper.selectList(
            new QueryWrapper<TeamSubscriptionEntity>()
                .in("status", List.of("ACTIVE", "SUPERSEDED"))
                .le("starts_at", now)
                .orderByAsc("ends_at"));
        active.forEach(subscription -> processActive(subscription, now));

        TeamSubscriptionEntity queued;
        while ((queued = nextActivatable(now)) != null) {
            queued.status = "ACTIVE";
            queued.updatedAt = LocalDateTime.now();
            subscriptionMapper.updateById(queued);
            processActive(queued, now);
        }
    }

    private void processActive(TeamSubscriptionEntity subscription, LocalDateTime now) {
        grantDuePeriods(subscription, now);
        if (!subscription.endsAt.isAfter(now)) {
            subscription.status = "EXPIRED";
            subscription.nextGrantAt = null;
            subscription.updatedAt = LocalDateTime.now();
            subscriptionMapper.updateById(subscription);
        }
    }

    private TeamSubscriptionEntity nextActivatable(LocalDateTime now) {
        List<TeamSubscriptionEntity> candidates = subscriptionMapper.selectList(
            new QueryWrapper<TeamSubscriptionEntity>()
                .eq("status", "QUEUED")
                .le("starts_at", now)
                .orderByAsc("starts_at", "id"));
        for (TeamSubscriptionEntity candidate : candidates) {
            Long activeCount = subscriptionMapper.selectCount(new QueryWrapper<TeamSubscriptionEntity>()
                .eq("tenant_id", candidate.tenantId)
                .eq("status", "ACTIVE")
                .le("starts_at", now)
                .gt("ends_at", now));
            if (activeCount == 0) return candidate;
        }
        return null;
    }

    private void grantDuePeriods(TeamSubscriptionEntity subscription, LocalDateTime now) {
        while (subscription.nextGrantAt != null
            && !subscription.nextGrantAt.isAfter(now)
            && subscription.nextGrantAt.isBefore(subscription.endsAt)) {
            int periodNo = nextPeriodNo(subscription.id);
            if (!grantPeriod(subscription, periodNo)) return;
            LocalDateTime next = subscription.startsAt.plusMonths(periodNo);
            subscription.nextGrantAt = next.isBefore(subscription.endsAt) ? next : null;
            subscription.updatedAt = LocalDateTime.now();
            subscriptionMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TeamSubscriptionEntity>()
                    .eq("id", subscription.id).set("next_grant_at", subscription.nextGrantAt)
                    .set("updated_at", subscription.updatedAt));
        }
    }

    private int nextPeriodNo(Long subscriptionId) {
        CommercialEntitlementGrantEntity latest = grantMapper.selectOne(
            new QueryWrapper<CommercialEntitlementGrantEntity>()
                .eq("subscription_id", subscriptionId)
                .eq("entitlement_type", "PERIODIC_POINTS")
                .orderByDesc("period_no")
                .last("limit 1"));
        if (latest == null) return 1;
        return "FAILED".equals(latest.status) ? latest.periodNo : latest.periodNo + 1;
    }

    private boolean grantPeriod(TeamSubscriptionEntity subscription, int periodNo) {
        CommercialEntitlementEntity entitlement = entitlementMapper.selectOne(
            new QueryWrapper<CommercialEntitlementEntity>()
                .eq("package_version_id", subscription.packageVersionId)
                .eq("entitlement_type", "PERIODIC_POINTS"));
        if (entitlement == null) return true;
        CommercialOrderEntity order = subscription.sourceOrderId == null ? null : orderMapper.selectById(subscription.sourceOrderId);
        AdminTenantGrantEntity adminGrant = subscription.sourceAdminGrantId == null ? null : adminGrants.selectById(subscription.sourceAdminGrantId);
        Long operatorId = adminGrant != null ? adminGrant.operatorId : order == null ? null : order.userId;
        if (operatorId == null) throw new IllegalStateException("Subscription fulfillment source is missing");
        String key = "subscription:" + subscription.id + ":period:" + periodNo;
        CommercialEntitlementGrantEntity grant = grantMapper.selectOne(
            new QueryWrapper<CommercialEntitlementGrantEntity>()
                .eq("tenant_id", subscription.tenantId)
                .eq("idempotency_key", key));
        try {
            if (entitlement.numericValue.signum() > 0) accounting.grant(subscription.tenantId, operatorId, entitlement.numericValue, key, "会员周期积分发放");
            if (grant == null) grant = newGrant(subscription, order, periodNo, key);
            grant.amount = entitlement.numericValue;
            grant.status = "GRANTED";
            grant.errorMessage = null;
            grant.grantedAt = LocalDateTime.now();
            grant.updatedAt = grant.grantedAt;
            saveGrant(grant);
            return true;
        } catch (RuntimeException exception) {
            if (grant == null) grant = newGrant(subscription, order, periodNo, key);
            grant.amount = entitlement.numericValue;
            grant.status = "FAILED";
            grant.errorMessage = exception.getMessage();
            grant.updatedAt = LocalDateTime.now();
            saveGrant(grant);
            LOGGER.error("Commercial subscription grant failed: subscriptionId={}, periodNo={}", subscription.id, periodNo, exception);
            return false;
        }
    }

    private CommercialEntitlementGrantEntity newGrant(
        TeamSubscriptionEntity subscription,
        CommercialOrderEntity order,
        int periodNo,
        String key
    ) {
        CommercialEntitlementGrantEntity grant = new CommercialEntitlementGrantEntity();
        grant.tenantId = subscription.tenantId;
        grant.orderId = order == null ? null : order.id;
        grant.adminGrantId = subscription.sourceAdminGrantId;
        grant.subscriptionId = subscription.id;
        grant.periodNo = periodNo;
        grant.entitlementType = "PERIODIC_POINTS";
        grant.idempotencyKey = key;
        grant.createdAt = LocalDateTime.now();
        return grant;
    }

    private void saveGrant(CommercialEntitlementGrantEntity grant) {
        if (grant.id == null) grantMapper.insert(grant);
        else grantMapper.updateById(grant);
    }
}
