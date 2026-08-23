package com.antshorttv.video;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

record CreateVideoDecompositionBatchRequest(
    @NotBlank @Size(max = 200) String name,
    Long modelId,
    @NotEmpty @Size(max = 50) List<@Valid VideoUploadMetadataRequest> videos
) {
}

record VideoUploadMetadataRequest(
    @NotBlank @Size(max = 500) String fileName,
    @NotBlank @Size(max = 1000) String storagePath,
    @Size(max = 128) String mimeType,
    @NotNull @Min(1) Long fileSize,
    @DecimalMin("0.1") BigDecimal durationSeconds
) {
}

record RetryVideoDecompositionEpisodeRequest(
    @Size(max = 32) String phase
) {
}

record UpdateVideoDecompositionDraftRequest(
    @NotBlank @Size(max = 200000) String draftContent,
    @Min(0) @Max(9999) Integer expectedDraftVersion
) {
}

record ConfirmVideoDecompositionDraftRequest(
    @NotBlank @Size(max = 200000) String draftContent,
    @Min(0) @Max(9999) Integer expectedDraftVersion,
    @NotNull Long projectId,
    Long expectedCurrentScriptVersionId
) {
}
