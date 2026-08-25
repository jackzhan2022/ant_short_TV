package com.antshorttv.ai;

public record AiProviderPollingRequest(
    AiCapability capability,
    String externalTaskId,
    String idempotencyKey,
    Long executionId,
    Long attemptId,
    int executionVersion,
    String phase
) {
    public AiProviderPollingRequest {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Provider polling idempotency key is required.");
        }
    }
}
