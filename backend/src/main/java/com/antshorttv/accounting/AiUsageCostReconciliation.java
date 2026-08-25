package com.antshorttv.accounting;

public record AiUsageCostReconciliation(
    Long executionId,
    int usageLineCount,
    int costLineCount,
    boolean balanced
) {
}
