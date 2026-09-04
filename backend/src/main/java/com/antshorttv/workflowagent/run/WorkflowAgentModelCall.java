package com.antshorttv.workflowagent.run;

public record WorkflowAgentModelCall(
    Long callLogId,
    Long modelId,
    Long providerId,
    String providerRequestId,
    String transportOutcome,
    String businessOutcome,
    Long attemptId
) {
    public WorkflowAgentModelCall(
        Long callLogId, Long modelId, Long providerId, String providerRequestId,
        String transportOutcome, String businessOutcome
    ) {
        this(callLogId, modelId, providerId, providerRequestId, transportOutcome, businessOutcome, null);
    }
}
