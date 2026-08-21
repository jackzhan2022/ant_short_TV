package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiGateway extends AiGateway {
    private final AiModelRouter aiModelRouter;
    private final AiCallLogWriter aiCallLogWriter;

    public DefaultAiGateway(AiModelRouter aiModelRouter, AiCallLogWriter aiCallLogWriter) {
        this.aiModelRouter = aiModelRouter;
        this.aiCallLogWriter = aiCallLogWriter;
    }

    @Override
    public AiTextResponse text(AiContext context, AiTextRequest request) {
        validateContext(context);
        AiModelRoute route = aiModelRouter.route(context.modelId(), "TEXT");
        long started = System.currentTimeMillis();
        try {
            AiTextResponse response = route.adapter().text(route.provider(), route.providerConfig(), route.model(), request);
            record(context.withModelId(route.model().getId()), route, "TEXT", request.userPrompt(), response.content(), "SUCCESS", null, started, response);
            return response;
        } catch (AiGatewayException exception) {
            record(context.withModelId(route.model().getId()), route, "TEXT", request.userPrompt(), null, "FAILED", exception.getMessage(), started, null);
            throw exception;
        }
    }

    @Override
    public AiImageResponse image(AiContext context, AiImageRequest request) {
        validateContext(context);
        AiModelRoute route = aiModelRouter.route(context.modelId(), "IMAGE");
        long started = System.currentTimeMillis();
        try {
            AiImageResponse response = route.adapter().image(route.provider(), route.providerConfig(), route.model(), request);
            record(context.withModelId(route.model().getId()), route, "IMAGE", request.prompt(), "generated=%d".formatted(response.imageUrls().size()), "SUCCESS", null, started, response);
            return response;
        } catch (AiGatewayException exception) {
            record(context.withModelId(route.model().getId()), route, "IMAGE", request.prompt(), null, "FAILED", exception.getMessage(), started, null);
            throw exception;
        }
    }

    private void validateContext(AiContext context) {
        if (context == null || context.tenantId() == null || context.userId() == null || context.projectId() == null) {
            throw new AiGatewayException(ErrorCode.VALIDATION_ERROR, "AI 调用上下文不完整。");
        }
    }

    private Long record(
        AiContext context,
        AiModelRoute route,
        String serviceType,
        String requestSummary,
        String responseSummary,
        String status,
        String errorMessage,
        long started,
        Object response
    ) {
        return aiCallLogWriter.record(new AiInvocationLogRequest(
            context,
            route,
            AiCapability.valueOf(serviceType),
            requestSummary,
            responseSummary,
            status,
            errorMessage,
            System.currentTimeMillis() - started,
            providerRequestId(response),
            tokens(response, "prompt"),
            tokens(response, "completion"),
            tokens(response, "total")
        ));
    }

    private String providerRequestId(Object response) {
        if (response instanceof AiTextResponse textResponse) {
            return textResponse.providerRequestId();
        }
        if (response instanceof AiImageResponse imageResponse) {
            return imageResponse.providerRequestId();
        }
        return null;
    }

    private Integer tokens(Object response, String type) {
        if (response instanceof AiTextResponse textResponse) {
            return switch (type) {
                case "prompt" -> textResponse.promptTokens();
                case "completion" -> textResponse.completionTokens();
                case "total" -> textResponse.totalTokens();
                default -> null;
            };
        }
        return null;
    }

}
