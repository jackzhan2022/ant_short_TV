package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;
import com.antshorttv.video.QwenVideoUnderstandingAdapter;
import com.antshorttv.video.VideoUnderstandingRequest;
import com.antshorttv.video.VideoUnderstandingResponse;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class AiInvocationService {
    @Autowired(required = false)
    private AiModelParameterProfileMapper parameterProfileMapper;
    private final AiModelRouter aiModelRouter;
    private final AiCallLogWriter aiCallLogWriter;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final AiInvocationErrorMapper errorMapper;
    private final QwenVideoUnderstandingAdapter qwenVideoUnderstandingAdapter;

    public AiInvocationService(
        AiModelRouter aiModelRouter,
        AiCallLogWriter aiCallLogWriter,
        PromptTemplateRenderer promptTemplateRenderer,
        AiInvocationErrorMapper errorMapper,
        QwenVideoUnderstandingAdapter qwenVideoUnderstandingAdapter
    ) {
        this.aiModelRouter = aiModelRouter;
        this.aiCallLogWriter = aiCallLogWriter;
        this.promptTemplateRenderer = promptTemplateRenderer;
        this.errorMapper = errorMapper;
        this.qwenVideoUnderstandingAdapter = qwenVideoUnderstandingAdapter;
    }

    public AiInvocationResult<AiTextResponse> invokeText(AiInvocationRequest request) {
        AiInvocationRequest effectiveRequest = withRenderedTextPrompt(request);
        AiModelRoute route = aiModelRouter.route(effectiveRequest.modelId(), AiCapability.TEXT.modelServiceType());
        long started = System.currentTimeMillis();
        try {
            AiTextResponse response = route.adapter().text(
                route.provider(),
                route.providerConfig(),
                route.model(),
                effectiveRequest.textRequest(),
                effectiveRequest.idempotencyKey()
            );
            Long logId = aiCallLogWriter.record(AiInvocationLogRequest.successText(
                effectiveRequest.toAiContext().withModelId(route.model().getId()),
                route,
                logSummary(effectiveRequest),
                response,
                elapsed(started, response.durationMs())
            ));
            return AiInvocationResult.text(effectiveRequest.businessSceneCode(), response, logId, route)
                .withCorrelation(effectiveRequest);
        } catch (Exception exception) {
            AiGatewayException normalized = errorMapper.normalize(exception, AiCapability.TEXT);
            throw recordFailure(effectiveRequest, route, AiCapability.TEXT, effectiveRequest.effectiveRequestSummary(), normalized, started);
        }
    }

    public AiInvocationResult<AiImageResponse> invokeImage(AiInvocationRequest request) {
        AiModelRoute route = aiModelRouter.route(request.modelId(), AiCapability.IMAGE.modelServiceType());
        long started = System.currentTimeMillis();
        try {
            AiImageResponse response = route.adapter().image(
                route.provider(),
                route.providerConfig(),
                route.model(),
                request.imageRequest(),
                request.idempotencyKey()
            );
            String responseSummary = response.imageUrls() == null ? "generated=0" : "generated=%d".formatted(response.imageUrls().size());
            Long logId = aiCallLogWriter.record(AiInvocationLogRequest.success(
                request.toAiContext().withModelId(route.model().getId()),
                route,
                AiCapability.IMAGE,
                logSummary(request),
                responseSummary,
                elapsed(started, response.durationMs()),
                response.providerRequestId(),
                null,
                null,
                null
            ));
            return AiInvocationResult.success(
                AiCapability.IMAGE,
                request.businessSceneCode(),
                response,
                responseSummary,
                logId,
                response.providerRequestId(),
                route.model().getId(),
                route.provider().getId(),
                route.provider().getCode(),
                null,
                null,
                null,
                response.durationMs()
            ).withCorrelation(request);
        } catch (Exception exception) {
            AiGatewayException normalized = errorMapper.normalize(exception, AiCapability.IMAGE);
            throw recordFailure(request, route, AiCapability.IMAGE, request.effectiveRequestSummary(), normalized, started);
        }
    }

    public AiInvocationResult<VideoUnderstandingResponse> invokeVideoUnderstanding(AiInvocationRequest request) {
        VideoUnderstandingRequest videoRequest = requireVideoUnderstandingRequest(request);
        AiModelRoute route = aiModelRouter.route(request.modelId(), AiCapability.VIDEO_UNDERSTANDING.modelServiceType());
        long started = System.currentTimeMillis();
        try {
            VideoUnderstandingResponse response = qwenVideoUnderstandingAdapter.videoUnderstanding(
                route.provider(),
                route.providerConfig(),
                route.model(),
                videoRequest
            );
            Long logId = aiCallLogWriter.record(AiInvocationLogRequest.success(
                request.toAiContext().withModelId(route.model().getId()),
                route,
                AiCapability.VIDEO_UNDERSTANDING,
                logSummary(request),
                response.content(),
                elapsed(started, response.durationMs()),
                response.providerRequestId(),
                response.promptTokens(),
                response.completionTokens(),
                response.totalTokens()
            ));
            return AiInvocationResult.success(
                AiCapability.VIDEO_UNDERSTANDING,
                request.businessSceneCode(),
                response,
                response.content(),
                logId,
                response.providerRequestId(),
                route.model().getId(),
                route.provider().getId(),
                route.provider().getCode(),
                response.promptTokens(),
                response.completionTokens(),
                response.totalTokens(),
                response.durationMs()
            ).withCorrelation(request);
        } catch (Exception exception) {
            AiGatewayException normalized = errorMapper.normalize(exception, AiCapability.VIDEO_UNDERSTANDING);
            throw recordFailure(request, route, AiCapability.VIDEO_UNDERSTANDING, videoRequest.videoUrl(), normalized, started);
        }
    }

    public <T> AiInvocationResult<T> submit(AiInvocationRequest request, Object payload) {
        AiModelRoute route = aiModelRouter.route(request.modelId(), request.capability().modelServiceType());
        long started = System.currentTimeMillis();
        try {
            AiProviderExecutionOutcome<T> outcome = route.adapter().submit(
                route.provider(),
                route.providerConfig(),
                route.model(),
                new AiProviderSubmissionRequest(
                    request.capability(), payload, request.idempotencyKey(), request.executionId(),
                    request.attemptId(), request.executionVersion(), request.phase()
                )
            );
            return recordProviderOutcome(request, route, outcome, String.valueOf(payload), started);
        } catch (Exception exception) {
            AiGatewayException normalized = errorMapper.normalize(exception, request.capability());
            throw recordFailure(request, route, request.capability(), String.valueOf(payload), normalized, started);
        }
    }

    public <T> AiInvocationResult<T> poll(AiInvocationRequest request, String externalTaskId) {
        AiModelRoute route = aiModelRouter.route(request.modelId(), request.capability().modelServiceType());
        long started = System.currentTimeMillis();
        try {
            AiProviderExecutionOutcome<T> outcome = route.adapter().poll(
                route.provider(),
                route.providerConfig(),
                route.model(),
                new AiProviderPollingRequest(
                    request.capability(), externalTaskId, request.idempotencyKey(), request.executionId(),
                    request.attemptId(), request.executionVersion(), request.phase()
                )
            );
            return recordProviderOutcome(request, route, outcome, externalTaskId, started);
        } catch (Exception exception) {
            AiGatewayException normalized = errorMapper.normalize(exception, request.capability());
            throw recordFailure(request, route, request.capability(), externalTaskId, normalized, started);
        }
    }

    public <T> AiInvocationResult<T> invokeProviderNative(
        AiInvocationRequest request,
        String requestSummary,
        AiProviderOperation<T> operation
    ) {
        AiModelRoute route = aiModelRouter.route(request.modelId(), request.capability().modelServiceType());
        long started = System.currentTimeMillis();
        try {
            return recordProviderOutcome(
                request,
                route,
                operation.execute(route),
                requestSummary,
                started
            );
        } catch (Exception exception) {
            AiGatewayException normalized = errorMapper.normalize(exception, request.capability());
            throw recordFailure(request, route, request.capability(), requestSummary, normalized, started);
        }
    }

    public void markBusinessFailure(Long callLogId, ErrorCode errorCode, String errorMessage) {
        aiCallLogWriter.markBusinessFailure(callLogId, errorCode, errorMessage);
    }

    private <T> AiInvocationResult<T> recordProviderOutcome(
        AiInvocationRequest request,
        AiModelRoute route,
        AiProviderExecutionOutcome<T> outcome,
        String requestSummary,
        long started
    ) {
        String responseSummary = outcome.outcome() == AiProviderExecutionState.ACCEPTED
            ? "accepted externalTaskId=" + outcome.externalTaskId()
            : String.valueOf(outcome.response());
        if (outcome.outcome() == AiProviderExecutionState.ACCEPTED) {
            Long logId = aiCallLogWriter.record(AiInvocationLogRequest.accepted(
                request.toAiContext().withModelId(route.model().getId()),
                route,
                request.capability(),
                requestSummary(request, requestSummary),
                Math.max(1, System.currentTimeMillis() - started),
                outcome.providerRequestId(),
                outcome.externalTaskId()
            ));
            AiProviderReconciliationStatus reconciliationStatus = outcome.reconciliationStatus() == null
                ? AiProviderReconciliationStatus.REQUIRED
                : outcome.reconciliationStatus();
            return AiInvocationResult.<T>accepted(
                request.capability(), request.businessSceneCode(), logId, outcome.providerRequestId(),
                outcome.externalTaskId(), route.model().getId(), route.provider().getId(),
                route.provider().getCode(), outcome.pollAfter(), reconciliationStatus
            ).withCorrelation(request);
        }
        Long logId = aiCallLogWriter.record(AiInvocationLogRequest.success(
            request.toAiContext().withModelId(route.model().getId()),
            route,
            request.capability(),
            requestSummary(request, requestSummary),
            responseSummary,
            Math.max(1, System.currentTimeMillis() - started),
            outcome.providerRequestId(),
            null,
            null,
            null
        ));
        return AiInvocationResult.success(
            request.capability(), request.businessSceneCode(), outcome.response(), responseSummary,
            logId, outcome.providerRequestId(), route.model().getId(), route.provider().getId(),
            route.provider().getCode(), null, null, null,
            Math.max(1, System.currentTimeMillis() - started)
        ).withCorrelation(request);
    }

    private AiInvocationRequest withRenderedTextPrompt(AiInvocationRequest request) {
        if (request.textRequest() != null) {
            return request;
        }
        if (request.promptTemplateId() == null || request.promptTemplateId().isBlank()) {
            throw new AiGatewayException(ErrorCode.VALIDATION_ERROR, "AI 文本请求不能为空。");
        }
        String prompt = promptTemplateRenderer.render(request.promptTemplateId(), request.templateVariables());
        AiInvocationRequest.Builder renderedBuilder = AiInvocationRequest.text()
            .tenantId(request.tenantId())
            .userId(request.userId())
            .projectId(request.projectId())
            .taskId(request.taskId())
            .modelId(request.modelId())
            .scene(request.scene())
            .businessSceneCode(request.businessSceneCode())
            .traceId(request.traceId())
            .executionId(request.executionId())
            .attemptId(request.attemptId())
            .executionVersion(request.executionVersion())
            .phase(request.phase())
            .idempotencyKey(request.idempotencyKey())
            .promptTemplateId(request.promptTemplateId())
            .templateVariables(request.templateVariables())
            .agentCode(request.agentCode())
            .textParameters(request.textRequest() == null ? request.textTemperature() : request.textRequest().temperature(),
                request.textRequest() == null ? request.textMaxTokens() : request.textRequest().maxTokens(),
                request.textRequest() == null ? request.textTopP() : request.textRequest().topP(),
                request.textRequest() == null ? request.textJsonMode() : request.textRequest().jsonMode(),
                request.textRequest() == null ? request.textTimeoutSeconds() : request.textRequest().timeoutSeconds(),
                request.textRequest() == null ? request.textRetryCount() : request.textRequest().retryCount());
        if (parameterProfileMapper != null && request.modelId() != null) {
            AiModelParameterProfileEntity profile = parameterProfileMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiModelParameterProfileEntity>()
                .eq(AiModelParameterProfileEntity::getModelId, request.modelId())
                .eq(AiModelParameterProfileEntity::getPublished, true)
                .orderByDesc(AiModelParameterProfileEntity::getVersionNo)
                .last("limit 1"));
            if (profile != null) {
                renderedBuilder.textParameters(profile.getTemperature(), profile.getMaxTokens(), profile.getTopP(),
                    profile.getJsonMode(), profile.getTimeoutSeconds(), profile.getRetryCount());
            }
        }
        return renderedBuilder.userPrompt(prompt).build();
    }

    private VideoUnderstandingRequest requireVideoUnderstandingRequest(AiInvocationRequest request) {
        if (request.videoRequest() instanceof VideoUnderstandingRequest videoRequest) {
            return videoRequest;
        }
        throw new AiGatewayException(ErrorCode.VALIDATION_ERROR, "视频理解请求不能为空。");
    }

    private AiGatewayException recordFailure(
        AiInvocationRequest request,
        AiModelRoute route,
        AiCapability capability,
        String requestSummary,
        AiGatewayException exception,
        long started
    ) {
        Long logId = aiCallLogWriter.record(AiInvocationLogRequest.failed(
            request.toAiContext().withModelId(route.model().getId()),
            route,
            capability,
            requestSummary(request, requestSummary),
            exception.getErrorCode().name() + " " + exception.getMessage(),
            Math.max(1, System.currentTimeMillis() - started)
        ));
        return new AiGatewayException(exception.getErrorCode(), exception.getMessage(), logId)
            .withCorrelation(request);
    }

    private long elapsed(long started, Long providerDurationMs) {
        return providerDurationMs == null ? Math.max(1, System.currentTimeMillis() - started) : providerDurationMs;
    }

    private String logSummary(AiInvocationRequest request) {
        return requestSummary(request, request.effectiveRequestSummary());
    }

    private String requestSummary(AiInvocationRequest request, String summary) {
        if (request.agentCode() == null || request.agentCode().isBlank()) {
            return summary;
        }
        return "[Agent:%s] %s".formatted(request.agentCode(), summary == null ? "" : summary);
    }
}
