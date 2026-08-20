package com.antshorttv.ai;

public record AiContext(
    Long tenantId,
    Long userId,
    Long projectId,
    Long taskId,
    Long modelId,
    String businessType,
    String traceId
) {
    public AiContext withModelId(Long resolvedModelId) {
        return new AiContext(tenantId, userId, projectId, taskId, resolvedModelId, businessType, traceId);
    }
}
