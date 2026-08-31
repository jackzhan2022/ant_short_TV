package com.antshorttv.workflowagent.run;

public record WorkflowAgentModelCall(
    Long callLogId,
    Long modelId,
    Long providerId,
    String providerRequestId,
    String transportOutcome,
    String businessOutcome
) {
}
