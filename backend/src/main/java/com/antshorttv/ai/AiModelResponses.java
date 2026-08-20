package com.antshorttv.ai;

import java.time.LocalDateTime;
import java.util.List;

record PlatformProviderResponse(
    Long id,
    String name,
    String code,
    String supportedTypes,
    String defaultBaseUrl,
    String baseUrl,
    String apiKey,
    String description,
    String status,
    String lastTestStatus,
    String lastTestMessage,
    LocalDateTime lastTestAt,
    LocalDateTime updatedAt
) {
}

record PlatformModelResponse(
    Long id,
    Long providerId,
    String providerName,
    String code,
    String name,
    String modelCode,
    String serviceType,
    String description,
    String status,
    Boolean isDefault,
    Integer sort,
    List<String> capabilities,
    LocalDateTime updatedAt
) {
}

record ProjectModelOptionResponse(
    Long id,
    String name,
    String description
) {
}

record ProjectAiModelsResponse(
    List<ProjectModelOptionResponse> textModels,
    List<ProjectModelOptionResponse> imageModels,
    List<ProjectModelOptionResponse> videoModels,
    List<ProjectModelOptionResponse> audioModels
) {
}

record ProjectAiConfigResponse(
    Long projectId,
    Long textModelId,
    Long imageModelId,
    Long videoModelId,
    Long audioModelId
) {
}
