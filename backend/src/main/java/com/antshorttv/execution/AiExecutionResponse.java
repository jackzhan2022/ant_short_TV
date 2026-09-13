package com.antshorttv.execution;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AiExecutionResponse(
    Long id,
    Long tenantId,
    Long projectId,
    String scene,
    String businessType,
    Long businessId,
    String status,
    String phase,
    Integer progress,
    Integer executionVersion,
    Long sourceExecutionId,
    Long rootExecutionId,
    boolean retryable,
    String resultType,
    Long resultId,
    String errorCode,
    String errorMessage,
    String usageCostStatus,
    String providerCostSummaryJson,
    Integer businessCallCount,
    Integer technicalRetryCount,
    String pointSettlementStatus,
    BigDecimal reservedPoints,
    BigDecimal settledPoints,
    BigDecimal releasedPoints,
    LocalDateTime startedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime completedAt,
    LocalDateTime canceledAt,
    String admission,
    Long conflictExecutionId
) {

    public AiExecutionResponse withAdmission(String admission, Long conflictExecutionId) {
        return new AiExecutionResponse(id, tenantId, projectId, scene, businessType, businessId, status, phase,
            progress, executionVersion, sourceExecutionId, rootExecutionId, retryable, resultType, resultId,
            errorCode, errorMessage, usageCostStatus, providerCostSummaryJson, businessCallCount,
            technicalRetryCount, pointSettlementStatus, reservedPoints, settledPoints, releasedPoints, startedAt,
            createdAt, updatedAt, completedAt, canceledAt, admission, conflictExecutionId);
    }
}
