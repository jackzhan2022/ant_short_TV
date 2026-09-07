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
    String businessOutcome,
    Integer responseLength,
    String finishReason,
    Boolean truncated,
    Integer cachedInputTokens,
    Integer cacheWriteTokens,
    String promptCacheKey
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
        Integer totalTokens,
        String externalTaskId,
        String transportOutcome,
        String businessOutcome
    ) {
        this(
            context, route, capability, requestSummary, responseSummary, status, errorMessage,
            durationMs, providerRequestId, promptTokens, completionTokens, totalTokens,
            externalTaskId, transportOutcome, businessOutcome, null, null, false, null, null, null
        );
    }

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
            "FAILED".equals(status) ? "NOT_REACHED" : "SUCCEEDED",
            null, null, false, null, null, null
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
            "SUCCEEDED",
            null,
            null,
            false,
            null,
            null,
            null
        );
    }

    public static AiInvocationLogRequest successText(
        AiContext context,
        AiModelRoute route,
        String requestSummary,
        AiTextResponse response,
        Long durationMs
    ) {
        return new AiInvocationLogRequest(
            context, route, AiCapability.TEXT, requestSummary, response.content(), "SUCCESS", null,
            durationMs, response.providerRequestId(), response.promptTokens(), response.completionTokens(),
            response.totalTokens(), null, "SUCCEEDED", "SUCCEEDED",
            response.content() == null ? 0 : response.content().length(), response.finishReason(), response.truncated(),
            response.cachedInputTokens(), response.cacheWriteTokens(), metadataString(response, "promptCacheKey")
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
            providerRequestId, null, null, null, externalTaskId, "SUCCEEDED", "PENDING",
            null, null, false, null, null, null
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
            "NOT_REACHED",
            null,
            null,
            false,
            null,
            null,
            null
        );
    }

    private static String metadataString(AiTextResponse response, String key) {
        if (response.metadata() == null) return null;
        Object value = response.metadata().get(key);
        return value == null ? null : value.toString();
    }
}
