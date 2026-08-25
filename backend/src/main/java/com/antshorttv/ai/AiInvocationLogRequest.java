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
    Integer totalTokens,
    String externalTaskId,
    String transportOutcome,
    String businessOutcome
) {
    public AiInvocationLogRequest(
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
        this(
            context, route, capability, requestSummary, responseSummary, status, errorMessage,
            durationMs, providerRequestId, promptTokens, completionTokens, totalTokens, null,
            "FAILED".equals(status) ? "FAILED" : "SUCCEEDED",
            "FAILED".equals(status) ? "NOT_REACHED" : "SUCCEEDED"
        );
    }

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
            totalTokens,
            null,
            "SUCCEEDED",
            "SUCCEEDED"
        );
    }

    public static AiInvocationLogRequest accepted(
        AiContext context,
        AiModelRoute route,
        AiCapability capability,
        String requestSummary,
        Long durationMs,
        String providerRequestId,
        String externalTaskId
    ) {
        return new AiInvocationLogRequest(
            context, route, capability, requestSummary, null, "ACCEPTED", null, durationMs,
            providerRequestId, null, null, null, externalTaskId, "SUCCEEDED", "PENDING"
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
            null,
            null,
            "FAILED",
            "NOT_REACHED"
        );
    }
}
