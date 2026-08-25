package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.common.ErrorCode;
import com.antshorttv.video.QwenVideoUnderstandingAdapter;
import com.antshorttv.video.VideoUnderstandingRequest;
import com.antshorttv.video.VideoUnderstandingResponse;
import java.util.Map;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiInvocationServiceTest {

    private final AiModelRouter router = mock(AiModelRouter.class);
    private final AiCallLogWriter logWriter = mock(AiCallLogWriter.class);
    private final PromptTemplateRenderer promptRenderer = mock(PromptTemplateRenderer.class);
    private final AiInvocationErrorMapper errorMapper = new AiInvocationErrorMapper();
    private final QwenVideoUnderstandingAdapter qwenAdapter = mock(QwenVideoUnderstandingAdapter.class);
    private final AiInvocationService service = new AiInvocationService(
        router,
        logWriter,
        promptRenderer,
        errorMapper,
        qwenAdapter
    );

    @Test
    void invokesTextThroughUnifiedContractAndReturnsLogId() {
        AiModelRoute route = route(AiCapability.TEXT);
        when(router.route(501L, "TEXT")).thenReturn(route);
        when(route.adapter().text(any(), any(), any(), any()))
            .thenReturn(new AiTextResponse("剧本内容", "req-text", 1, 2, 3, 44L, Map.of()));
        when(logWriter.record(any(AiInvocationLogRequest.class))).thenReturn(9001L);

        AiInvocationResult<AiTextResponse> result = service.invokeText(AiInvocationRequest.text()
            .tenantId(1L)
            .userId(2L)
            .projectId(3L)
            .taskId(4L)
            .modelId(501L)
            .scene(AiBusinessScene.SCRIPT_GENERATE)
            .traceId("trace-text")
            .userPrompt("写一集")
            .build());

        assertThat(result.content()).isEqualTo("剧本内容");
        assertThat(result.aiCallLogId()).isEqualTo(9001L);
        assertThat(result.providerRequestId()).isEqualTo("req-text");
        assertThat(result.businessSceneCode()).isEqualTo("script_generate");
        verify(logWriter).record(any(AiInvocationLogRequest.class));
    }

    @Test
    void invokesVideoUnderstandingThroughUnifiedContract() {
        AiModelRoute route = route(AiCapability.VIDEO_UNDERSTANDING);
        when(router.route(601L, "VIDEO_UNDERSTANDING")).thenReturn(route);
        when(qwenAdapter.videoUnderstanding(any(), any(), any(), any()))
            .thenReturn(new VideoUnderstandingResponse("{\"characters\":[]}", "req-video", 5, 6, 11, 88L, Map.of()));
        when(logWriter.record(any(AiInvocationLogRequest.class))).thenReturn(9002L);

        AiInvocationResult<VideoUnderstandingResponse> result = service.invokeVideoUnderstanding(AiInvocationRequest.videoUnderstanding()
            .tenantId(1L)
            .userId(2L)
            .projectId(3L)
            .taskId(4L)
            .modelId(601L)
            .scene(AiBusinessScene.VIDEO_UNDERSTANDING)
            .videoRequest(new VideoUnderstandingRequest("https://cdn.example.com/1.mp4", "只返回合法 JSON"))
            .build());

        assertThat(result.content()).contains("characters");
        assertThat(result.aiCallLogId()).isEqualTo(9002L);
        assertThat(result.providerRequestId()).isEqualTo("req-video");
        assertThat(result.capability()).isEqualTo(AiCapability.VIDEO_UNDERSTANDING);
    }

    @Test
    void invokesImageThroughUnifiedContractAndReturnsLogId() {
        AiModelRoute route = route(AiCapability.IMAGE);
        when(router.route(701L, "IMAGE")).thenReturn(route);
        when(route.adapter().image(any(), any(), any(), any()))
            .thenReturn(new AiImageResponse(java.util.List.of("https://cdn.example.com/image.png"), "req-image", 55L, Map.of()));
        when(logWriter.record(any(AiInvocationLogRequest.class))).thenReturn(9004L);

        AiInvocationResult<AiImageResponse> result = service.invokeImage(AiInvocationRequest.image()
            .tenantId(1L)
            .userId(2L)
            .projectId(3L)
            .taskId(4L)
            .modelId(701L)
            .businessSceneCode("image_generate")
            .imageRequest(new AiImageRequest("角色图", null, "1024x1024", null, 1, java.util.List.of()))
            .build());

        assertThat(result.aiCallLogId()).isEqualTo(9004L);
        assertThat(result.providerRequestId()).isEqualTo("req-image");
        assertThat(result.content()).isEqualTo("generated=1");
        assertThat(result.capability()).isEqualTo(AiCapability.IMAGE);
    }

    @Test
    void invokesScriptReviewThroughUnifiedContractWithResolvedAgentContext() {
        AiModelRoute route = route(AiCapability.TEXT);
        when(router.route(801L, "TEXT")).thenReturn(route);
        when(route.adapter().text(any(), any(), any(), any()))
            .thenReturn(new AiTextResponse("{\"overallScore\":90,\"conclusion\":\"PASS\",\"issues\":[]}", "req-review", 1, 2, 3, 66L, Map.of()));
        when(logWriter.record(any(AiInvocationLogRequest.class))).thenReturn(9005L);

        AiInvocationResult<AiTextResponse> result = service.invokeText(AiInvocationRequest.text()
            .tenantId(1L)
            .userId(2L)
            .projectId(3L)
            .modelId(801L)
            .scene(AiBusinessScene.SCRIPT_REVIEW)
            .userPrompt("审核剧本")
            .build());

        assertThat(result.businessSceneCode()).isEqualTo(AiBusinessScene.SCRIPT_REVIEW.code());
        assertThat(result.aiCallLogId()).isEqualTo(9005L);
        ArgumentCaptor<AiInvocationLogRequest> logCaptor = ArgumentCaptor.forClass(AiInvocationLogRequest.class);
        verify(logWriter).record(logCaptor.capture());
        assertThat(logCaptor.getValue().requestSummary()).startsWith("[Agent:script-review]");
    }


    @Test
    void recordsProviderFailureWithNormalizedError() {
        AiModelRoute route = route(AiCapability.TEXT);
        when(router.route(501L, "TEXT")).thenReturn(route);
        when(route.adapter().text(any(), any(), any(), any()))
            .thenThrow(new AiGatewayException(ErrorCode.AI_RATE_LIMIT, "too many requests"));
        when(logWriter.record(any(AiInvocationLogRequest.class))).thenReturn(9003L);

        assertThatThrownBy(() -> service.invokeText(AiInvocationRequest.text()
                .tenantId(1L)
                .userId(2L)
                .projectId(3L)
                .modelId(501L)
                .scene(AiBusinessScene.SCRIPT_GENERATE)
                .executionId(91L)
                .attemptId(92L)
                .executionVersion(3)
                .phase("SUBMIT")
                .idempotencyKey("execution-91-v3-submit")
                .traceId("trace-91")
                .userPrompt("写一集")
                .build()))
            .isInstanceOf(AiGatewayException.class)
            .satisfies(exception -> {
                AiGatewayException aiException = (AiGatewayException) exception;
                assertThat(aiException.getErrorCode()).isEqualTo(ErrorCode.AI_RATE_LIMIT);
                assertThat(aiException.getAiCallLogId()).isEqualTo(9003L);
                assertThat(aiException.getExecutionId()).isEqualTo(91L);
                assertThat(aiException.getAttemptId()).isEqualTo(92L);
                assertThat(aiException.getExecutionVersion()).isEqualTo(3);
                assertThat(aiException.getPhase()).isEqualTo("SUBMIT");
                assertThat(aiException.getIdempotencyKey()).isEqualTo("execution-91-v3-submit");
                assertThat(aiException.getTraceId()).isEqualTo("trace-91");
            });

        verify(logWriter).record(any(AiInvocationLogRequest.class));
    }

    @Test
    void submitsProviderWorkWithStableIdempotencyAndReturnsAcceptedCorrelation() {
        AiProviderAdapter adapter = mock(AiProviderAdapter.class);
        AiModelRoute route = route(AiCapability.IMAGE, adapter);
        when(router.route(701L, "IMAGE")).thenReturn(route);
        doReturn(AiProviderExecutionOutcome.accepted(
            "provider-request-accepted",
            "external-task-1",
            Duration.ofSeconds(4),
            null
        )).when(adapter).submit(any(), any(), any(), any());
        when(logWriter.record(any(AiInvocationLogRequest.class))).thenReturn(9010L);

        AiInvocationRequest request = correlatedImageRequest("SUBMIT", "execution-91-v3-submit");
        AiInvocationResult<String> result = service.submit(request, "image-payload");

        assertThat(result.providerExecutionState()).isEqualTo(AiProviderExecutionState.ACCEPTED);
        assertThat(result.externalTaskId()).isEqualTo("external-task-1");
        assertThat(result.reconciliationStatus()).isEqualTo(AiProviderReconciliationStatus.REQUIRED);
        assertThat(result.executionId()).isEqualTo(91L);
        assertThat(result.attemptId()).isEqualTo(92L);
        assertThat(result.executionVersion()).isEqualTo(3);
        assertThat(result.phase()).isEqualTo("SUBMIT");
        assertThat(result.idempotencyKey()).isEqualTo("execution-91-v3-submit");
        assertThat(result.traceId()).isEqualTo("trace-91");
        assertThat(result.transportOutcome()).isEqualTo("SUCCEEDED");
        assertThat(result.businessOutcome()).isEqualTo("PENDING");

        ArgumentCaptor<AiProviderSubmissionRequest> captor = ArgumentCaptor.forClass(AiProviderSubmissionRequest.class);
        verify(adapter).submit(any(), any(), any(), captor.capture());
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("execution-91-v3-submit");
        assertThat(captor.getValue().executionId()).isEqualTo(91L);
    }

    @Test
    void pollsProviderWorkWithStablePhaseKeyAndReturnsCompletedOutcome() {
        AiProviderAdapter adapter = mock(AiProviderAdapter.class);
        AiModelRoute route = route(AiCapability.IMAGE, adapter);
        when(router.route(701L, "IMAGE")).thenReturn(route);
        doReturn(AiProviderExecutionOutcome.completed("final-image", "provider-request-poll"))
            .when(adapter).poll(any(), any(), any(), any());
        when(logWriter.record(any(AiInvocationLogRequest.class))).thenReturn(9011L);

        AiInvocationRequest request = correlatedImageRequest("POLL", "execution-91-v3-poll-1");
        AiInvocationResult<String> result = service.poll(request, "external-task-1");

        assertThat(result.providerExecutionState()).isEqualTo(AiProviderExecutionState.COMPLETED);
        assertThat(result.response()).isEqualTo("final-image");
        assertThat(result.businessOutcome()).isEqualTo("SUCCEEDED");
        assertThat(result.phase()).isEqualTo("POLL");

        ArgumentCaptor<AiProviderPollingRequest> captor = ArgumentCaptor.forClass(AiProviderPollingRequest.class);
        verify(adapter).poll(any(), any(), any(), captor.capture());
        assertThat(captor.getValue().externalTaskId()).isEqualTo("external-task-1");
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("execution-91-v3-poll-1");
    }

    private AiInvocationRequest correlatedImageRequest(String phase, String idempotencyKey) {
        return AiInvocationRequest.image()
            .tenantId(1L)
            .userId(2L)
            .projectId(3L)
            .modelId(701L)
            .businessSceneCode("image_generate")
            .executionId(91L)
            .attemptId(92L)
            .executionVersion(3)
            .phase(phase)
            .idempotencyKey(idempotencyKey)
            .traceId("trace-91")
            .build();
    }

    private AiModelRoute route(AiCapability capability) {
        return route(capability, mock(AiProviderAdapter.class));
    }

    private AiModelRoute route(AiCapability capability, AiProviderAdapter adapter) {
        AiModelEntity model = new AiModelEntity();
        model.setId(501L);
        model.setName(capability.name() + " Model");
        model.setServiceType(capability.modelServiceType());
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(601L);
        provider.setCode("OpenAI");
        return new AiModelRoute(model, provider, new AiProviderConfigEntity(), adapter);
    }
}
