package com.antshorttv.points;

import com.antshorttv.accounting.AiModelBillingResolver;
import com.antshorttv.commercial.CommercialEntitlementResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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
        var at = LocalDateTime.now();
        Long version = plan.frozenPointPriceVersionId() == null
            ? billing.requireComplete(plan.modelId(), plan.usage().keySet(), plan.dimensions(), at).pointVersionId()
            : plan.frozenPointPriceVersionId();
        var discount = plan.frozenDiscountRate() == null
            ? entitlements.resolveGlobalDiscount(tenantId, at).discountRate() : plan.frozenDiscountRate();
        BigDecimal points = settlements.calculateModelPoints(version, plan.usage(), plan.dimensions())
            .multiply(discount).setScale(8, RoundingMode.HALF_UP);
        return new Line(plan.label(), plan.modelId(), version, points, plan.estimated(), plan.trialEligible());
    }

    public record Line(String label, Long modelId, Long pointPriceVersionId, BigDecimal points,
                       boolean estimated, boolean trialEligible) {}
}
