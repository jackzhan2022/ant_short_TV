package com.antshorttv.points;

import com.antshorttv.accounting.AiUsageMetric;
import java.math.BigDecimal;
import java.util.Map;

/** Read-only usage plan. No provider call, execution, reservation or ledger write. */
public record AiPointEstimatePlan(
    String label, Long modelId, Map<AiUsageMetric, BigDecimal> usage,
    Map<String, String> dimensions, boolean estimated,
    Long frozenPointPriceVersionId, BigDecimal frozenDiscountRate, boolean trialEligible
) {
    public AiPointEstimatePlan(String label, Long modelId, Map<AiUsageMetric, BigDecimal> usage,
                               Map<String, String> dimensions, boolean estimated) {
        this(label, modelId, usage, dimensions, estimated, null, null, true);
    }

    public AiPointEstimatePlan paidOnly() {
        return new AiPointEstimatePlan(label, modelId, usage, dimensions, estimated,
            frozenPointPriceVersionId, frozenDiscountRate, false);
    }

    public AiPointEstimatePlan frozen(AiPointReservationEntity reservation) {
        return new AiPointEstimatePlan(label, modelId, usage, dimensions, estimated,
            reservation.pointPriceVersionId, reservation.discountRate, Boolean.TRUE.equals(reservation.trialEligible));
    }
}
