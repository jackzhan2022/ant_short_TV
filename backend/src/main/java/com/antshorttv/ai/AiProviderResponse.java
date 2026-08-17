package com.antshorttv.ai;

public record AiProviderResponse(
    Long id,
    String name,
    String code,
    String supportedTypes,
    String defaultBaseUrl,
    String recommendedModels,
    String description,
    String status
) {

    static AiProviderResponse from(AiProviderEntity entity) {
        return new AiProviderResponse(
            entity.getId(),
            entity.getName(),
            entity.getCode(),
            entity.getSupportedTypes(),
            entity.getDefaultBaseUrl(),
            entity.getRecommendedModels(),
            entity.getDescription(),
            entity.getStatus()
        );
    }
}
