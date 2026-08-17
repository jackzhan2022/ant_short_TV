package com.antshorttv.ai;

import java.time.LocalDateTime;

public record AiServiceConfigResponse(
    Long id,
    Long tenantId,
    String name,
    String provider,
    String serviceType,
    String baseUrl,
    String apiKey,
    String model,
    String endpoint,
    String queryEndpoint,
    Integer priority,
    Boolean isDefault,
    Boolean enabled,
    String lastTestStatus,
    String lastTestMessage,
    LocalDateTime lastTestAt,
    String remark,
    LocalDateTime updatedAt
) {

    static AiServiceConfigResponse from(AiServiceConfigEntity entity, String maskedApiKey) {
        return new AiServiceConfigResponse(
            entity.getId(),
            entity.getTenantId(),
            entity.getName(),
            entity.getProvider(),
            entity.getServiceType(),
            entity.getBaseUrl(),
            maskedApiKey,
            entity.getModel(),
            entity.getEndpoint(),
            entity.getQueryEndpoint(),
            entity.getPriority(),
            entity.getIsDefault(),
            entity.getEnabled(),
            entity.getLastTestStatus(),
            entity.getLastTestMessage(),
            entity.getLastTestAt(),
            entity.getRemark(),
            entity.getUpdatedAt()
        );
    }
}
