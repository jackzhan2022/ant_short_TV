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
    String pointSettlementStatus,
    BigDecimal reservedPoints,
    BigDecimal settledPoints,
    BigDecimal releasedPoints,
    LocalDateTime startedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime completedAt,
    LocalDateTime canceledAt
) {
}
