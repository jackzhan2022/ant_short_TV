package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;
import com.antshorttv.video.QwenVideoUnderstandingAdapter;
import com.antshorttv.video.VideoUnderstandingRequest;
import com.antshorttv.video.VideoUnderstandingResponse;
import org.springframework.stereotype.Service;

@Service
public class AiInvocationService {
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
                effectiveRequest.textRequest()
            );
            Long logId = aiCallLogWriter.record(AiInvocationLogRequest.success(
                effectiveRequest.toAiContext().withModelId(route.model().getId()),
                route,
                AiCapability.TEXT,
                logSummary(effectiveRequest),
                response.content(),
                elapsed(started, response.durationMs()),
                response.providerRequestId(),
                response.promptTokens(),
                response.completionTokens(),
                response.totalTokens()
            ));
            return AiInvocationResult.text(effectiveRequest.businessSceneCode(), response, logId, route);
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
                request.imageRequest()
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
            );
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
            );
        } catch (Exception exception) {
            AiGatewayException normalized = errorMapper.normalize(exception, AiCapability.VIDEO_UNDERSTANDING);
            throw recordFailure(request, route, AiCapability.VIDEO_UNDERSTANDING, videoRequest.videoUrl(), normalized, started);
        }
    }

    public void markBusinessFailure(Long callLogId, ErrorCode errorCode, String errorMessage) {
        aiCallLogWriter.markBusinessFailure(callLogId, errorCode, errorMessage);
    }

    private AiInvocationRequest withRenderedTextPrompt(AiInvocationRequest request) {
        if (request.textRequest() != null) {
            return request;
        }
        if (request.promptTemplateId() == null || request.promptTemplateId().isBlank()) {
            throw new AiGatewayException(ErrorCode.VALIDATION_ERROR, "AI 文本请求不能为空。");
        }
        String prompt = promptTemplateRenderer.render(request.promptTemplateId(), request.templateVariables());
        return AiInvocationRequest.text()
            .tenantId(request.tenantId())
            .userId(request.userId())
            .projectId(request.projectId())
            .taskId(request.taskId())
            .modelId(request.modelId())
            .scene(request.scene())
            .businessSceneCode(request.businessSceneCode())
            .traceId(request.traceId())
            .promptTemplateId(request.promptTemplateId())
            .templateVariables(request.templateVariables())
            .agentCode(request.agentCode())
            .userPrompt(prompt)
            .build();
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
        return new AiGatewayException(exception.getErrorCode(), exception.getMessage(), logId);
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
