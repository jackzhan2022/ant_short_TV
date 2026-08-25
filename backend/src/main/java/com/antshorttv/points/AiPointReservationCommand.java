package com.antshorttv.points;

import com.antshorttv.accounting.AiUsageMetric;
import java.math.BigDecimal;
import java.util.Map;

public record AiPointReservationCommand(
    Long tenantId,
    Long userId,
    Long executionId,
    int executionVersion,
    String scene,
    String businessType,
    Long businessId,
    Long modelId,
    String capability,
    Map<AiUsageMetric, BigDecimal> authorizedUsage,
    Map<String, String> dimensions,
    String idempotencyKey
) {
    public AiPointReservationCommand(
        Long tenantId,
        Long userId,
        Long executionId,
        int executionVersion,
        String scene,
        String businessType,
        Long businessId,
        Map<AiUsageMetric, BigDecimal> authorizedUsage,
        Map<String, String> dimensions,
        String idempotencyKey
    ) {
        this(
            tenantId, userId, executionId, executionVersion, scene, businessType, businessId,
            null, null, authorizedUsage, dimensions, idempotencyKey
        );
    }

    public AiPointReservationCommand {
        authorizedUsage = authorizedUsage == null ? Map.of() : Map.copyOf(authorizedUsage);
        dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
    }
}
