package com.antshorttv.video;

import com.antshorttv.ai.AiContext;
import com.antshorttv.ai.AiCallLogWriter;
import com.antshorttv.ai.AiCapability;
import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.ai.AiInvocationLogRequest;
import com.antshorttv.ai.AiModelRoute;
import com.antshorttv.ai.AiModelRouter;
import com.antshorttv.common.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class VideoUnderstandingGateway {
    private final AiModelRouter aiModelRouter;
    private final QwenVideoUnderstandingAdapter qwenAdapter;
    private final AiCallLogWriter aiCallLogWriter;

    public VideoUnderstandingGateway(
        AiModelRouter aiModelRouter,
        QwenVideoUnderstandingAdapter qwenAdapter,
        AiCallLogWriter aiCallLogWriter
    ) {
        this.aiModelRouter = aiModelRouter;
        this.qwenAdapter = qwenAdapter;
        this.aiCallLogWriter = aiCallLogWriter;
    }

    public VideoUnderstandingCallResult call(AiContext context, VideoUnderstandingRequest request) {
        validateContext(context);
        AiModelRoute route = aiModelRouter.route(context.modelId(), "VIDEO_UNDERSTANDING");
        long started = System.currentTimeMillis();
        try {
            VideoUnderstandingResponse response = qwenAdapter.videoUnderstanding(
                route.provider(),
                route.providerConfig(),
                route.model(),
                request
            );
            Long callLogId = record(
                context.withModelId(route.model().getId()),
                route,
                request.videoUrl(),
                response.content(),
                "SUCCESS",
                null,
                started,
                response
            );
            return new VideoUnderstandingCallResult(response, callLogId);
        } catch (AiGatewayException exception) {
            record(
                context.withModelId(route.model().getId()),
                route,
                request.videoUrl(),
                null,
                "FAILED",
                exception.getMessage(),
                started,
                null
            );
            throw exception;
        }
    }

    public void markBusinessFailure(Long callLogId, String errorMessage) {
        if (callLogId == null) {
            return;
        }
        aiCallLogWriter.markBusinessFailure(callLogId, ErrorCode.AI_RESPONSE_INVALID, errorMessage);
    }

    private void validateContext(AiContext context) {
        if (context == null || context.tenantId() == null || context.userId() == null || context.projectId() == null) {
            throw new AiGatewayException(ErrorCode.VALIDATION_ERROR, "AI 调用上下文不完整。");
        }
    }

    private Long record(
        AiContext context,
        AiModelRoute route,
        String requestSummary,
        String responseSummary,
        String status,
        String errorMessage,
        long started,
        VideoUnderstandingResponse response
    ) {
        return aiCallLogWriter.record(new AiInvocationLogRequest(
            context,
            route,
            AiCapability.VIDEO_UNDERSTANDING,
            requestSummary,
            responseSummary,
            status,
            errorMessage,
            System.currentTimeMillis() - started,
            response == null ? null : response.providerRequestId(),
            response == null ? null : response.promptTokens(),
            response == null ? null : response.completionTokens(),
            response == null ? null : response.totalTokens()
        ));
    }
}
