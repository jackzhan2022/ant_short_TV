package com.antshorttv.execution;

public record AiExecutionClaim(
    Long executionId,
    Long attemptId,
    String claimToken,
    int executionVersion,
    String phase
) {
}
