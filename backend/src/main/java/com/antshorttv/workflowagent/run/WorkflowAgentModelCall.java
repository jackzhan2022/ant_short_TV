package com.antshorttv.workflowagent.run;

public record WorkflowAgentModelCall(
    Long callLogId,
    Long modelId,
    Long providerId,
    String providerRequestId,
    String transportOutcome,
    String businessOutcome,
    Long attemptId,
    Integer promptTokens,
    Integer completionTokens,
    Integer cachedInputTokens,
    Integer cacheWriteTokens
) {
    public WorkflowAgentModelCall(
        Long callLogId, Long modelId, Long providerId, String providerRequestId,
        String transportOutcome, String businessOutcome
    ) {
        this(callLogId, modelId, providerId, providerRequestId, transportOutcome, businessOutcome,
            null, null, null, null, null);
    }

    public WorkflowAgentModelCall(
        Long callLogId, Long modelId, Long providerId, String providerRequestId,
        String transportOutcome, String businessOutcome, Long attemptId
    ) {
        this(callLogId, modelId, providerId, providerRequestId, transportOutcome, businessOutcome,
            attemptId, null, null, null, null);
    }
}
