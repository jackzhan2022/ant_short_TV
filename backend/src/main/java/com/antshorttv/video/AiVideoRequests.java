package com.antshorttv.video;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record CreateAiVideoTaskRequest(
    @NotNull Long storyboardId,
    Long modelId,
    @NotBlank @Size(max = 3000) String prompt,
    @Size(max = 1000) String negativePrompt,
    Long firstFrameImageId,
    @Size(max = 1000) String firstFrameUrl,
    Long lastFrameImageId,
    @Size(max = 1000) String lastFrameUrl,
    Integer durationSeconds,
    @Size(max = 32) String aspectRatio,
    @Size(max = 32) String resolution,
    Boolean generateAudio,
    Boolean watermark,
    @Size(max = 64) String cameraMovement,
    @Size(max = 32) String motionStrength,
    Long randomSeed
) {
}
