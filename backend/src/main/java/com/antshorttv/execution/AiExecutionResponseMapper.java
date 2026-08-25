package com.antshorttv.execution;

import org.springframework.stereotype.Component;

@Component
public class AiExecutionResponseMapper {
    public AiExecutionResponse toResponse(AiExecutionTaskEntity task) {
        return new AiExecutionResponse(
            task.id,
            task.tenantId,
            task.projectId,
            task.scene,
            task.businessType,
            task.businessId,
            task.status,
            task.phase,
            task.progress,
            task.executionVersion,
            task.sourceExecutionId,
            task.rootExecutionId,
            Boolean.TRUE.equals(task.retryable),
            task.resultType,
            task.resultId,
            task.errorCode,
            task.errorMessage,
            task.usageCostStatus,
            task.providerCostSummaryJson,
            task.pointSettlementStatus,
            task.reservedPoints,
            task.settledPoints,
            task.releasedPoints,
            task.startedAt,
            task.createdAt,
            task.updatedAt,
            task.completedAt,
            task.canceledAt
        );
    }
}
