package com.antshorttv.accounting;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record AiUsageCommand(
    AiUsageContext context,
    AiUsageMetric metric,
    BigDecimal quantity,
    AiUsageSource source,
    Map<String, String> dimensions,
    LocalDateTime observedAt,
    Long adjustmentOfUsageLineId
) {
    public AiUsageCommand {
        dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
        if (quantity == null || metric == null || context == null) {
            throw new IllegalArgumentException("Usage context, metric, and quantity are required.");
        }
    }

    public static AiUsageCommand providerReported(
        AiUsageContext context, AiUsageMetric metric, String quantity,
        Map<String, String> dimensions, LocalDateTime observedAt
    ) {
        return new AiUsageCommand(
            context, metric, new BigDecimal(quantity), AiUsageSource.PROVIDER_REPORTED,
            dimensions, observedAt, null
        );
    }

    public static AiUsageCommand requestDerived(
        AiUsageContext context, AiUsageMetric metric, String quantity,
        Map<String, String> dimensions, LocalDateTime observedAt
    ) {
        return new AiUsageCommand(
            context, metric, new BigDecimal(quantity), AiUsageSource.REQUEST_DERIVED,
            dimensions, observedAt, null
        );
    }

    public static AiUsageCommand resultMeasured(
        AiUsageContext context, AiUsageMetric metric, String quantity,
        Map<String, String> dimensions, LocalDateTime observedAt
    ) {
        return new AiUsageCommand(
            context, metric, new BigDecimal(quantity), AiUsageSource.RESULT_MEASURED,
            dimensions, observedAt, null
        );
    }
}
