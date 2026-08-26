package com.antshorttv.commercial;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class CommercialEntitlementResolver {
    private static final BigDecimal NO_DISCOUNT = BigDecimal.ONE.setScale(8, RoundingMode.UNNECESSARY);

    private final TeamSubscriptionMapper subscriptionMapper;
    private final CommercialEntitlementMapper entitlementMapper;

    public CommercialEntitlementResolver(
        TeamSubscriptionMapper subscriptionMapper,
        CommercialEntitlementMapper entitlementMapper
    ) {
        this.subscriptionMapper = subscriptionMapper;
        this.entitlementMapper = entitlementMapper;
    }

    public CommercialDiscountSnapshot resolveGlobalDiscount(Long tenantId, LocalDateTime at) {
        TeamSubscriptionEntity subscription = subscriptionMapper.selectOne(
            new QueryWrapper<TeamSubscriptionEntity>()
                .eq("tenant_id", tenantId)
                .eq("status", "ACTIVE")
                .le("starts_at", at)
                .gt("ends_at", at)
                .orderByDesc("starts_at")
                .last("limit 1"));
        if (subscription == null) return CommercialDiscountSnapshot.none();

        CommercialEntitlementEntity entitlement = entitlementMapper.selectOne(
            new QueryWrapper<CommercialEntitlementEntity>()
                .eq("package_version_id", subscription.packageVersionId)
                .eq("entitlement_type", "GLOBAL_DISCOUNT"));
        if (entitlement == null || entitlement.numericValue == null) return CommercialDiscountSnapshot.none();
        return new CommercialDiscountSnapshot(
            subscription.id,
            subscription.packageVersionId,
            entitlement.numericValue.setScale(8, RoundingMode.HALF_UP));
    }

    public record CommercialDiscountSnapshot(
        Long subscriptionId,
        Long packageVersionId,
        BigDecimal discountRate
    ) {
        static CommercialDiscountSnapshot none() {
            return new CommercialDiscountSnapshot(null, null, NO_DISCOUNT);
        }
    }
}
