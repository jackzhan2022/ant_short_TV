package com.antshorttv.ai;

public record AiInvocationLogRequest(
    AiContext context,
    AiModelRoute route,
    AiCapability capability,
    String requestSummary,
    String responseSummary,
    String status,
    String errorMessage,
    Long durationMs,
    String providerRequestId,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens
) {
    public static AiInvocationLogRequest success(
        AiContext context,
        AiModelRoute route,
        AiCapability capability,
        String requestSummary,
        String responseSummary,
        Long durationMs,
        String providerRequestId,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
    ) {
        return new AiInvocationLogRequest(
            context,
            route,
            capability,
            requestSummary,
            responseSummary,
            "SUCCESS",
            null,
            durationMs,
            providerRequestId,
            promptTokens,
            completionTokens,
            totalTokens
        );
    }

    public static AiInvocationLogRequest failed(
        AiContext context,
        AiModelRoute route,
        AiCapability capability,
        String requestSummary,
        String errorMessage,
        Long durationMs
    ) {
        return new AiInvocationLogRequest(
            context,
            route,
            capability,
            requestSummary,
            null,
            "FAILED",
            errorMessage,
            durationMs,
            null,
            null,
            null,
            null
        );
    }
}
