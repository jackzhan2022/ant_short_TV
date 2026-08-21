package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;

public record AiInvocationResult<T>(
    AiCapability capability,
    String businessSceneCode,
    T response,
    String content,
    Long aiCallLogId,
    String providerRequestId,
    Long resolvedModelId,
    Long providerId,
    String providerCode,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    Long durationMs,
    String status,
    ErrorCode errorCode,
    String errorMessage
) {
    public static AiInvocationResult<AiTextResponse> text(
        String businessSceneCode,
        AiTextResponse response,
        Long aiCallLogId,
        AiModelRoute route
    ) {
        return new AiInvocationResult<>(
            AiCapability.TEXT,
            businessSceneCode,
            response,
            response == null ? null : response.content(),
            aiCallLogId,
            response == null ? null : response.providerRequestId(),
            route == null ? null : route.model().getId(),
            route == null ? null : route.provider().getId(),
            route == null ? null : route.provider().getCode(),
            response == null ? null : response.promptTokens(),
            response == null ? null : response.completionTokens(),
            response == null ? null : response.totalTokens(),
            response == null ? null : response.durationMs(),
            "SUCCESS",
            null,
            null
        );
    }

    public static <T> AiInvocationResult<T> success(
        AiCapability capability,
        String businessSceneCode,
        T response,
        String content,
        Long aiCallLogId,
        String providerRequestId,
        Long resolvedModelId,
        Long providerId,
        String providerCode,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long durationMs
    ) {
        return new AiInvocationResult<>(
            capability,
            businessSceneCode,
            response,
            content,
            aiCallLogId,
            providerRequestId,
            resolvedModelId,
            providerId,
            providerCode,
            promptTokens,
            completionTokens,
            totalTokens,
            durationMs,
            "SUCCESS",
            null,
            null
        );
    }
}
