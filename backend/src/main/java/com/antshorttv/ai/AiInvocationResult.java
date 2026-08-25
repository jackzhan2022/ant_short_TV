package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;
import java.time.Duration;

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
    String errorMessage,
    AiProviderExecutionState providerExecutionState,
    String externalTaskId,
    Duration pollAfter,
    AiProviderReconciliationStatus reconciliationStatus,
    String transportOutcome,
    String businessOutcome,
    Long executionId,
    Long attemptId,
    Integer executionVersion,
    String phase,
    String idempotencyKey,
    String traceId
) {
    public AiInvocationResult(
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
        this(
            capability, businessSceneCode, response, content, aiCallLogId, providerRequestId,
            resolvedModelId, providerId, providerCode, promptTokens, completionTokens, totalTokens,
            durationMs, status, errorCode, errorMessage,
            AiProviderExecutionState.COMPLETED, null, null, AiProviderReconciliationStatus.NOT_REQUIRED,
            "FAILED".equals(status) ? "FAILED" : "SUCCEEDED",
            "FAILED".equals(status) ? "NOT_REACHED" : "SUCCEEDED",
            null, null, null, null, null, null
        );
    }

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

    public static <T> AiInvocationResult<T> accepted(
        AiCapability capability,
        String businessSceneCode,
        Long aiCallLogId,
        String providerRequestId,
        String externalTaskId,
        Long resolvedModelId,
        Long providerId,
        String providerCode,
        Duration pollAfter,
        AiProviderReconciliationStatus reconciliationStatus
    ) {
        return new AiInvocationResult<>(
            capability, businessSceneCode, null, null, aiCallLogId, providerRequestId,
            resolvedModelId, providerId, providerCode, null, null, null, null,
            "ACCEPTED", null, null,
            AiProviderExecutionState.ACCEPTED, externalTaskId, pollAfter, reconciliationStatus,
            "SUCCEEDED", "PENDING", null, null, null, null, null, null
        );
    }

    public AiInvocationResult<T> withCorrelation(AiInvocationRequest request) {
        return new AiInvocationResult<>(
            capability, businessSceneCode, response, content, aiCallLogId, providerRequestId,
            resolvedModelId, providerId, providerCode, promptTokens, completionTokens, totalTokens,
            durationMs, status, errorCode, errorMessage, providerExecutionState, externalTaskId,
            pollAfter, reconciliationStatus, transportOutcome, businessOutcome,
            request.executionId(), request.attemptId(), request.executionVersion(), request.phase(),
            request.idempotencyKey(), request.traceId()
        );
    }
}
