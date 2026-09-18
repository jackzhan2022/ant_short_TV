package com.antshorttv.aiimage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

record AssetImageBatchRequest(
    @NotBlank String assetType,
    @NotEmpty @Size(max = 500) List<@NotNull Long> assetIds,
    @NotBlank String mode,
    Long modelId,
    @NotBlank String aspectRatio,
    @NotNull @Min(1) @Max(4) Integer imageCount
) {
}

record AssetImageBatchPreflightResponse(
    String assetType,
    String mode,
    int selectedAssets,
    int plannedTasks,
    int waitingDependencies,
    int skippedCompleted,
    int skippedGenerating,
    int skippedMissingPrompt,
    int skippedOther,
    int totalImages
) {
}

record AssetImageBatchItemResponse(
    Long id,
    Long assetId,
    Long variantId,
    String variantName,
    String stage,
    String status,
    Long dependencyItemId,
    Long taskId,
    String errorMessage
) {
}

record AssetImageBatchResponse(
    Long id,
    Long projectId,
    String assetType,
    String mode,
    String status,
    int total,
    int pending,
    int running,
    int succeeded,
    int failed,
    int skipped,
    Long modelId,
    String aspectRatio,
    Integer imageCount,
    List<AssetImageBatchItemResponse> items,
    LocalDateTime createdAt
) {
}
