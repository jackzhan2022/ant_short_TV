package com.antshorttv.points;

import com.antshorttv.accounting.AiModelBillingResolver;
import com.antshorttv.commercial.CommercialEntitlementResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import com.antshorttv.accounting.AiUsageMetric;
import org.springframework.stereotype.Service;

@Service
public class AiPointEstimateCalculator {
    private final AiModelBillingResolver billing;
    private final AiPointSettlementService settlements;
    private final CommercialEntitlementResolver entitlements;

    public AiPointEstimateCalculator(AiModelBillingResolver billing, AiPointSettlementService settlements,
                                    CommercialEntitlementResolver entitlements) {
        this.billing = billing;
        this.settlements = settlements;
        this.entitlements = entitlements;
    }

    public Line calculate(Long tenantId, AiPointEstimatePlan plan) {
        return calculate(tenantId, plan, new Context(tenantId));
    }

    /** One estimate request owns this context; prices and balances are never cached between requests. */
    public static final class Context {
        private final Long tenantId;
        private final LocalDateTime at = LocalDateTime.now();
        private final Map<BillingKey, Long> versions = new HashMap<>();
        private final Map<PriceKey, BigDecimal> prices = new HashMap<>();
        private BigDecimal discount;
        public Context(Long tenantId) { this.tenantId = tenantId; }
    }
    private record BillingKey(Long model, Set<AiUsageMetric> metrics, Map<String,String> dimensions) {}
    private record PriceKey(Long version, Map<AiUsageMetric,BigDecimal> usage, Map<String,String> dimensions) {}

    public Line calculate(Long tenantId, AiPointEstimatePlan plan, Context context) {
        if (!tenantId.equals(context.tenantId)) throw new IllegalArgumentException("Estimate context belongs to another team.");
        Long version = plan.frozenPointPriceVersionId() == null
            ? context.versions.computeIfAbsent(new BillingKey(plan.modelId(), Set.copyOf(plan.usage().keySet()), Map.copyOf(plan.dimensions())),
                key -> billing.requireComplete(key.model(), key.metrics(), key.dimensions(), context.at).pointVersionId())
            : plan.frozenPointPriceVersionId();
        if (plan.frozenDiscountRate() == null && context.discount == null)
            context.discount = entitlements.resolveGlobalDiscount(tenantId, context.at).discountRate();
        var discount = plan.frozenDiscountRate() == null ? context.discount : plan.frozenDiscountRate();
        BigDecimal points = context.prices.computeIfAbsent(new PriceKey(version, Map.copyOf(plan.usage()), Map.copyOf(plan.dimensions())),
                key -> settlements.calculateModelPoints(key.version(), key.usage(), key.dimensions()))
            .multiply(discount).setScale(8, RoundingMode.HALF_UP);
        if (plan.workbench()) points = WorkbenchPointPolicy.discounted(points);
        return new Line(plan.label(), plan.modelId(), version, points, plan.estimated(), plan.trialEligible());
    }

    public record Line(String label, Long modelId, Long pointPriceVersionId, BigDecimal points,
                       boolean estimated, boolean trialEligible) {}
}
