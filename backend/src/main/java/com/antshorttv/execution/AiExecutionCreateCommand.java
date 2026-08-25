package com.antshorttv.execution;

public record AiExecutionCreateCommand(
    Long tenantId,
    Long userId,
    Long projectId,
    String scene,
    String capability,
    String businessType,
    Long businessId,
    Long requestedModelId,
    String initialPhase,
    String clientIdempotencyKey,
    String traceId,
    boolean retryable,
    String redactedInputJson
) {
}
