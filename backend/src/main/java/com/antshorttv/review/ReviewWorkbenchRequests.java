package com.antshorttv.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

record CreateReviewProjectRequest(
    @Size(max = 200) String name
) {
}

record BindReviewProjectRequest(
    @NotNull Long mainProjectId
) {
}

record SaveReviewVersionRequest(
    @NotBlank @Size(max = 200000) String content,
    @Size(max = 255) String fileName,
    @Size(max = 32) String sourceType
) {
}

record CreateReviewTaskRequest(
    Long versionId,
    @NotBlank @Size(max = 32) String reviewMode,
    @NotNull List<@Size(max = 64) String> selectedDimensions,
    @NotBlank @Size(max = 32) String reviewScopeType,
    Map<String, Object> reviewScope,
    String taskName
) {
}

record RetryReviewTaskRequest(
    @Size(max = 32) String reviewMode,
    List<@Size(max = 64) String> selectedDimensions,
    @Size(max = 32) String reviewScopeType,
    Map<String, Object> reviewScope
) {
}

record UpdateReviewTaskRequest(
    @Size(max = 32) String reviewMode,
    List<@Size(max = 64) String> selectedDimensions,
    @Size(max = 32) String reviewScopeType,
    Map<String, Object> reviewScope
) {
}

record MarkReviewIssueResolvedRequest(
    @Size(max = 1000) String note
) {
}

record BatchRepairReviewRequest(
    @NotBlank @Size(max = 32) String actionType,
    @Size(max = 5000) String replacementFrom,
    @Size(max = 5000) String replacementTo,
    @Size(max = 5000) String insertionText,
    @Size(max = 5000) String deletionText,
    List<Long> selectedHitIds
) {
}

record RollbackReviewVersionRequest(
    @NotNull Long versionId
) {
}

record ExportReviewReportRequest(
    @NotBlank @Size(max = 32) String exportType,
    @NotNull Long versionId
) {
}
