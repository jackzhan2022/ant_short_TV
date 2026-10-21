package com.antshorttv.commercial;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.antshorttv.points.PointAccountingService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** Benefit fulfillment shared by payments and administrator grants. Caller owns the transaction. */
@Service
public class CommercialBenefitFulfillmentService {
    private final CommercialPackageMapper packageMapper;
    private final CommercialPackageVersionMapper versionMapper;
    private final CommercialEntitlementMapper entitlementMapper;
    private final CommercialEntitlementGrantMapper grantMapper;
    private final TeamSubscriptionMapper subscriptionMapper;
    private final PointAccountingService accounting;

    public CommercialBenefitFulfillmentService(CommercialPackageMapper packageMapper, CommercialPackageVersionMapper versionMapper,
        CommercialEntitlementMapper entitlementMapper, CommercialEntitlementGrantMapper grantMapper,
        TeamSubscriptionMapper subscriptionMapper, PointAccountingService accounting) {
        this.packageMapper = packageMapper;
        this.versionMapper = versionMapper;
        this.entitlementMapper = entitlementMapper;
        this.grantMapper = grantMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.accounting = accounting;
    }

    public record Source(Long tenantId, Long operatorId, Long packageVersionId, Long orderId,
        Long adminGrantId, String snapshot, LocalDateTime effectiveAt) {
        String key() { return orderId != null ? "order:" + orderId : "admin-grant:" + adminGrantId; }
    }

    public Long fulfill(Source source) {
        accounting.lockAccount(source.tenantId());
        CommercialPackageVersionEntity version = versionMapper.selectById(source.packageVersionId());
        CommercialPackageEntity pack = packageMapper.selectById(version.packageId);
        if (!List.of("POINT_PACKAGE", "SUBSCRIPTION").contains(pack.packageType)) {
            throw new IllegalStateException("Unsupported package type: " + pack.packageType);
        }
        List<CommercialEntitlementEntity> entitlements = entitlementMapper.selectList(
            new QueryWrapper<CommercialEntitlementEntity>().eq("package_version_id", version.id));
        entitlements.stream().filter(e -> "ONE_TIME_POINTS".equals(e.entitlementType) && e.numericValue.signum() > 0)
            .forEach(e -> grantPoints(source, null, null, e.numericValue, source.key() + ":points"));
        return "SUBSCRIPTION".equals(pack.packageType) ? activateSubscription(source, version, entitlements) : null;
    }

    private Long activateSubscription(Source source, CommercialPackageVersionEntity version,
        List<CommercialEntitlementEntity> entitlements) {
        LocalDateTime now = source.effectiveAt();
        boolean periodic = entitlements.stream().anyMatch(e -> "PERIODIC_POINTS".equals(e.entitlementType));
        TeamSubscriptionEntity current = subscriptionMapper.selectOne(new QueryWrapper<TeamSubscriptionEntity>()
            .eq("tenant_id", source.tenantId()).eq("status", "ACTIVE").le("starts_at", now).gt("ends_at", now)
            .orderByDesc("ends_at").last("limit 1"));
        if (current != null && current.packageVersionId.equals(version.id)) {
            LocalDateTime previousEnd = current.endsAt;
            current.endsAt = previousEnd.plusMonths(version.periodMonths);
            // The scheduler may have cleared the next date after granting the old final period.
            if (!periodic) current.nextGrantAt = null;
            else if (current.nextGrantAt == null) current.nextGrantAt = previousEnd;
            current.updatedAt = LocalDateTime.now();
            subscriptionMapper.update(null, new UpdateWrapper<TeamSubscriptionEntity>().eq("id", current.id)
                .set("ends_at", current.endsAt).set("next_grant_at", current.nextGrantAt).set("updated_at", current.updatedAt));
            shiftQueuedSubscriptions(source.tenantId(), version.periodMonths);
            return current.id;
        }
        // Retain previously purchased periodic grants while switching the effective package immediately.
        subscriptionMapper.update(null, new UpdateWrapper<TeamSubscriptionEntity>()
            .eq("tenant_id", source.tenantId()).eq("status", "ACTIVE")
            .set("status", "SUPERSEDED").set("updated_at", LocalDateTime.now()));
        TeamSubscriptionEntity sub = new TeamSubscriptionEntity();
        sub.tenantId = source.tenantId();
        sub.packageVersionId = version.id;
        sub.sourceOrderId = source.orderId();
        sub.sourceAdminGrantId = source.adminGrantId();
        sub.status = "ACTIVE";
        sub.startsAt = now;
        sub.endsAt = now.plusMonths(version.periodMonths);
        sub.nextGrantAt = periodic ? now.plusMonths(1) : null;
        sub.snapshotJson = source.snapshot();
        sub.createdAt = LocalDateTime.now();
        sub.updatedAt = sub.createdAt;
        subscriptionMapper.insert(sub);
        entitlements.stream().filter(e -> "PERIODIC_POINTS".equals(e.entitlementType))
            .forEach(e -> grantPoints(source, sub, 1, e.numericValue, "subscription:" + sub.id + ":period:1"));
        return sub.id;
    }

    private void shiftQueuedSubscriptions(Long tenantId, int months) {
        List<TeamSubscriptionEntity> queued = subscriptionMapper.selectList(new QueryWrapper<TeamSubscriptionEntity>()
            .eq("tenant_id", tenantId).eq("status", "QUEUED").orderByAsc("starts_at"));
        for (TeamSubscriptionEntity subscription : queued) {
            subscription.startsAt = subscription.startsAt.plusMonths(months);
            subscription.endsAt = subscription.endsAt.plusMonths(months);
            subscription.nextGrantAt = subscription.nextGrantAt.plusMonths(months);
            subscription.updatedAt = LocalDateTime.now();
            subscriptionMapper.updateById(subscription);
        }
    }

    private void grantPoints(Source source, TeamSubscriptionEntity sub, Integer periodNo, BigDecimal amount, String key) {
        CommercialEntitlementGrantEntity existing = grantMapper.selectOne(new QueryWrapper<CommercialEntitlementGrantEntity>()
            .eq("tenant_id", source.tenantId()).eq("idempotency_key", key));
        if (existing != null) return;
        if (amount.signum() > 0) accounting.grant(source.tenantId(), source.operatorId(), amount, key, "商业化权益发放");
        CommercialEntitlementGrantEntity grant = new CommercialEntitlementGrantEntity();
        grant.tenantId = source.tenantId();
        grant.orderId = source.orderId();
        grant.adminGrantId = source.adminGrantId();
        grant.subscriptionId = sub == null ? null : sub.id;
        grant.periodNo = periodNo;
        grant.entitlementType = sub == null ? "ONE_TIME_POINTS" : "PERIODIC_POINTS";
        grant.amount = amount;
        grant.status = "GRANTED";
        grant.idempotencyKey = key;
        grant.grantedAt = LocalDateTime.now();
        grant.createdAt = grant.grantedAt;
        grant.updatedAt = grant.grantedAt;
        grantMapper.insert(grant);
    }
}
