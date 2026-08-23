package com.antshorttv.review;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

record ReviewProjectSummaryResponse(
    Long id,
    String name,
    String sourceFileName,
    String sourceType,
    Long currentVersionId,
    Long lastTaskId,
    String status,
    Integer versionCount,
    Integer latestRoundNo,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

record ReviewProjectDetailResponse(
    ReviewProjectSummaryResponse project,
    List<ReviewVersionResponse> versions,
    List<ReviewTaskResponse> tasks
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
    String status,
    String currentStage,
    Integer overallProgress,
    String currentAction,
    String errorCode,
    String errorMessage,
    LocalDateTime completedAt,
    LocalDateTime canceledAt,
    ReviewReviewSummaryResponse summary,
    List<ReviewIssueResponse> issues
) {
}

record ReviewReviewSummaryResponse(
    String overallConclusion,
    Integer overallScore,
    String summary
) {
}

record ReviewIssueResponse(
    Long id,
    Long taskId,
    Long scriptVersionId,
    Integer roundNo,
    String issueNo,
    String dimension,
    String severity,
    String title,
    Map<String, Object> position,
    String excerpt,
    String problem,
    List<String> evidence,
    String suggestion,
    String status,
    String relatedIssueNo,
    Boolean manuallyResolved,
    LocalDateTime manuallyResolvedAt,
    Long manuallyResolvedBy,
    List<ReviewIssueHitResponse> hits
) {
}

record ReviewIssueHitResponse(
    Long id,
    Long issueId,
    Integer hitNo,
    Integer episodeNo,
    String sceneNo,
    Integer shotNo,
    Integer lineNo,
    String anchorLabel,
    String excerpt,
    String entityName,
    Boolean selected,
    String replacementText
) {
}

record ReviewBatchRepairResponse(
    Long id,
    Long taskId,
    Long issueId,
    String actionType,
    String status,
    List<Long> selectedHitIds,
    String replacementFrom,
    String replacementTo,
    String insertionText,
    String deletionText,
    LocalDateTime appliedAt
) {
}

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
    List<ReviewRoundHistoryResponse> roundHistory,
    List<ReviewIssueMappingResponse> issueMappings
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
    Integer issueCount,
    Integer processedIssueCount,
    ReviewReviewSummaryResponse summary,
    LocalDateTime completedAt
) {
}

record ReviewIssueMappingResponse(
    Long issueId,
    String issueNo,
    Integer roundNo,
    String status,
    String relatedIssueNo,
    String dimension,
    String title,
    Integer hitCount,
    List<Long> hitIds
) {
}
