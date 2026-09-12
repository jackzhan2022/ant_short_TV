package com.antshorttv.review;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

record ReviewProjectSummaryResponse(
    Long id,
    Long mainProjectId,
    String accessSource,
    String name,
    String sourceFileName,
    String sourceType,
    Long currentVersionId,
    Long lastTaskId,
    String status,
    Integer versionCount,
    Integer latestRoundNo,
    String reviewState,
    String actionLabel,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

record ReviewProjectListSummaryResponse(
    Long id,
    Long mainProjectId,
    String accessSource,
    String name,
    String sourceFileName,
    String sourceType,
    Long currentVersionId,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

record ReviewProjectMetricsResponse(
    Long projectId,
    Integer versionCount,
    Integer latestRoundNo,
    String reviewState,
    String actionLabel
) {}

record ReviewProjectDetailResponse(
    ReviewProjectSummaryResponse project,
    List<ReviewVersionResponse> versions,
    List<ReviewTaskResponse> tasks
) {
}

record ReviewProjectReviewHistoryResponse(
    ReviewProjectSummaryResponse project,
    List<ReviewVersionMetadataResponse> versions,
    List<ReviewHistoryTaskResponse> items,
    Integer page,
    Integer pageSize,
    Long total
) {
}

record ReviewVersionMetadataResponse(
    Long id,
    Long projectId,
    Integer versionNo,
    String sourceType,
    String fileName,
    LocalDateTime createdAt
) {
}

record ReviewHistoryTaskResponse(
    Long id,
    Long scriptVersionId,
    Integer roundNo,
    String reviewMode,
    List<String> selectedDimensions,
    String reviewScopeType,
    String reportMarkdown,
    String status,
    Integer overallProgress,
    Long createdBy,
    LocalDateTime createdAt,
    LocalDateTime completedAt,
    LocalDateTime canceledAt,
    String errorMessage
) {
}

record ReviewVersionResponse(
    Long id,
    Long projectId,
    Integer versionNo,
    String sourceType,
    String fileName,
    String content,
    LocalDateTime createdAt
) {
}

record ReviewTaskResponse(
    Long id,
    Long projectId,
    Long scriptVersionId,
    Integer roundNo,
    String reviewMode,
    List<String> selectedDimensions,
    String reviewScopeType,
    Map<String, Object> reviewScope,
    String reportMarkdown,
    String status,
    String currentStage,
    Integer overallProgress,
    String currentAction,
    String errorCode,
    String errorMessage,
    String workflowAgentCode,
    Long workflowAgentRevision,
    Long workflowAgentRunId,
    String workflowPhase,
    Integer workflowAttemptNo,
    Long fanoutSnapshotId,
    Long aggregationRunId,
    String retryKind,
    Boolean stale,
    ReviewFanoutProgressResponse fanout,
    ReviewObservabilityResponse observability,
    LocalDateTime completedAt,
    LocalDateTime canceledAt,
    ReviewVersionResponse boundVersion
) {
}

record ReviewFanoutProgressResponse(
    String status,
    Integer totalUnits,
    Integer completedUnits,
    Integer failedUnits,
    Long currentUnitId,
    String aggregationStatus,
    List<ReviewUnitProgressResponse> units
) {}

record ReviewUnitProgressResponse(
    Long id,
    Integer unitNo,
    String unitKey,
    String stageType,
    String dimension,
    String status,
    Long childRunId,
    Integer attemptNo,
    Boolean reportSaved,
    String errorCode,
    String errorMessage,
    ReviewCacheUsageResponse cacheUsage
) {}

record ReviewObservabilityResponse(
    ReviewCacheUsageResponse cacheUsage
) {}




record ReviewCacheUsageResponse(
    Long promptTokens,
    Long ordinaryInputTokens,
    Long cachedInputTokens,
    Long cacheWriteTokens,
    Long outputTokens,
    Long latencyMs,
    BigDecimal cacheHitRatio,
    Boolean cacheObservable
) {}





record ReviewExportRecordResponse(
    Long id,
    Long projectId,
    Long versionId,
    Long taskId,
    String exportType,
    String exportStatus,
    String fileName,
    Long fileSize,
    String downloadUrl,
    String errorMessage,
    LocalDateTime createdAt
) {
}

record ReviewVersionHistoryResponse(
    ReviewProjectSummaryResponse project,
    ReviewVersionResponse selectedVersion,
    List<ReviewVersionResponse> versions,
    List<ReviewVersionDiffResponse> diffLines,
    List<ReviewRoundHistoryResponse> roundHistory
) {
}

record ReviewVersionDiffResponse(
    Long fromVersionId,
    Long toVersionId,
    Integer addedLines,
    Integer removedLines,
    List<ReviewVersionDiffLineResponse> lines
) {
}

record ReviewVersionDiffLineResponse(
    String type,
    Integer lineNo,
    String beforeText,
    String afterText
) {
}

record ReviewRoundHistoryResponse(
    Long taskId,
    Integer roundNo,
    String status,
    String reviewMode,
    LocalDateTime completedAt
) {
}
