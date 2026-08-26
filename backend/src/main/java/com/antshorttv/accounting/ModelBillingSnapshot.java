package com.antshorttv.accounting;

import java.util.Map;

public record ModelBillingSnapshot(
    Long costVersionId,
    Long pointVersionId,
    Map<AiUsageMetric, AiModelPriceComponentEntity> costComponents,
    Map<AiUsageMetric, AiModelPointPriceComponentEntity> pointComponents
) {
}
