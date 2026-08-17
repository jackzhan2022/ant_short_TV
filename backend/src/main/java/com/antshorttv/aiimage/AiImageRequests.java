package com.antshorttv.aiimage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

record CreateAiImageTaskRequest(
    @NotBlank String taskType,
    @NotBlank String targetType,
    @NotNull Long targetId,
    Long serviceConfigId,
    @NotBlank @Size(max = 3000) String prompt,
    @Size(max = 1000) String negativePrompt,
    List<String> referenceImages,
    @NotBlank String aspectRatio,
    @NotNull @Min(1) @Max(4) Integer imageCount,
    String style,
    String quality,
    String seed
) {
}
