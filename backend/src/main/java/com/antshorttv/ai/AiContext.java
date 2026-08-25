package com.antshorttv.ai;

public record AiContext(
    Long tenantId,
    Long userId,
    Long projectId,
    Long taskId,
    Long modelId,
    String businessType,
    String traceId,
    Long executionId,
    Long attemptId,
    Integer executionVersion,
    String phase,
    String idempotencyKey
) {
    public AiContext(
        Long tenantId,
        Long userId,
        Long projectId,
        Long taskId,
        Long modelId,
        String businessType,
        String traceId
    ) {
        this(tenantId, userId, projectId, taskId, modelId, businessType, traceId, null, null, null, null, null);
    }

    public AiContext withModelId(Long resolvedModelId) {
        return new AiContext(
            tenantId, userId, projectId, taskId, resolvedModelId, businessType, traceId,
            executionId, attemptId, executionVersion, phase, idempotencyKey
        );
    }
}
