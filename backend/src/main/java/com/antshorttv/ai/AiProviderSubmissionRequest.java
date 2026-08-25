package com.antshorttv.ai;

public record AiProviderSubmissionRequest(
    AiCapability capability,
    Object payload,
    String idempotencyKey,
    Long executionId,
    Long attemptId,
    int executionVersion,
    String phase
) {
    public AiProviderSubmissionRequest {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Provider submission idempotency key is required.");
        }
    }
}
