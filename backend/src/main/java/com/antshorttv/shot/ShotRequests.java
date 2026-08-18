package com.antshorttv.shot;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

record CreateAiVoiceTaskRequest(
    @NotNull Long storyboardId,
    Long serviceConfigId,
    @NotBlank String voiceType,
    String speakerName,
    @NotBlank String voiceId,
    @NotBlank @Size(max = 2000) String textContent,
    @DecimalMin("0.5") @DecimalMax("2.0") BigDecimal speed,
    @DecimalMin("0.5") @DecimalMax("2.0") BigDecimal pitch,
    @DecimalMin("0.1") @DecimalMax("2.0") BigDecimal volume
) {
}

record CreateStoryboardSubtitleRequest(
    @NotNull Long storyboardId,
    Long voiceResultId,
    @NotBlank String subtitleType,
    @NotBlank @Size(max = 2000) String textContent,
    @DecimalMin("0.0") BigDecimal startTime,
    @DecimalMin("0.01") BigDecimal endTime,
    Map<String, Object> styleConfig
) {
}

record UpdateStoryboardSubtitleRequest(
    @NotBlank String textContent,
    @DecimalMin("0.0") BigDecimal startTime,
    @DecimalMin("0.01") BigDecimal endTime,
    Map<String, Object> styleConfig
) {
}

record CreateShotComposeTaskRequest(
    @NotNull Long storyboardId,
    Long voiceResultId,
    Long subtitleId,
    Boolean includeSubtitle,
    @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal audioVolume,
    String outputFormat
) {
}
