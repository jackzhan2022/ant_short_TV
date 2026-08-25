package com.antshorttv.accounting;

public record AiUsageContext(
    Long tenantId,
    Long executionId,
    Long attemptId,
    Long aiCallLogId,
    Long modelId
) {
}
