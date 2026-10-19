package com.antshorttv.points;

import com.antshorttv.accounting.AiUsageMetric;
import java.math.BigDecimal;
import java.util.Map;

/** Read-only usage plan. No provider call, execution, reservation or ledger write. */
public record AiPointEstimatePlan(
    String label, Long modelId, Map<AiUsageMetric, BigDecimal> usage,
    Map<String, String> dimensions, boolean estimated,
    Long frozenPointPriceVersionId, BigDecimal frozenDiscountRate, boolean trialEligible,
    Long projectId, boolean workbench
) {
    public AiPointEstimatePlan(String label, Long modelId, Map<AiUsageMetric, BigDecimal> usage,
                               Map<String, String> dimensions, boolean estimated) {
        this(label, modelId, usage, dimensions, estimated, null, null, true, null, false);
    }

    public AiPointEstimatePlan(String label, Long modelId, Map<AiUsageMetric, BigDecimal> usage,
                               Map<String, String> dimensions, boolean estimated,
                               Long frozenPointPriceVersionId, BigDecimal frozenDiscountRate, boolean trialEligible) {
        this(label, modelId, usage, dimensions, estimated, frozenPointPriceVersionId, frozenDiscountRate,
            trialEligible, null, false);
    }

    public AiPointEstimatePlan paidOnly() {
        return new AiPointEstimatePlan(label, modelId, usage, dimensions, estimated,
            frozenPointPriceVersionId, frozenDiscountRate, false, projectId, workbench);
    }

    public AiPointEstimatePlan frozen(AiPointReservationEntity reservation) {
        return new AiPointEstimatePlan(label, modelId, usage, dimensions, estimated,
            reservation.pointPriceVersionId, reservation.discountRate, Boolean.TRUE.equals(reservation.trialEligible),
            projectId, workbench);
    }

    public AiPointEstimatePlan workbench(Long projectId) {
        return new AiPointEstimatePlan(label, modelId, usage, dimensions, estimated,
            frozenPointPriceVersionId, frozenDiscountRate, trialEligible, projectId, true);
    }

    public AiPointEstimatePlan inProject(Long projectId) {
        return workbench ? workbench(projectId) : this;
    }
}
