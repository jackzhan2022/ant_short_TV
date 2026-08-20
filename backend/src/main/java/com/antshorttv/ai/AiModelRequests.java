package com.antshorttv.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record PlatformProviderRequest(
    @NotBlank @Size(max = 128) String name,
    @NotBlank @Size(max = 64) String code,
    @Size(max = 512) String baseUrl,
    @Size(max = 512) String defaultBaseUrl,
    @Size(max = 200) String supportedTypes,
    @Size(max = 1000) String description,
    String apiKey,
    Boolean enabled
) {
}

record PlatformModelRequest(
    @NotNull Long providerId,
    @NotBlank @Size(max = 128) String code,
    @NotBlank @Size(max = 128) String name,
    @NotBlank @Size(max = 256) String modelCode,
    @NotBlank @Size(max = 32) String serviceType,
    @Size(max = 1000) String description,
    Boolean enabled,
    Boolean isDefault,
    Integer sort,
    String configJson
) {
}

record ProjectAiConfigRequest(
    Long textModelId,
    Long imageModelId,
    Long videoModelId,
    Long audioModelId
) {
}
