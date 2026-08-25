package com.antshorttv.ai;

import java.time.LocalDateTime;

public record AiCallLogResponse(
    Long id,
    Long tenantId,
    Long userId,
    Long taskId,
    Long modelId,
    Long providerId,
    String provider,
    String serviceType,
    String model,
    String businessScene,
    String requestSummary,
    String responseSummary,
    String status,
    String errorMessage,
    Long durationMs,
    String traceId,
    String providerRequestId,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    LocalDateTime createdAt
) {
}
