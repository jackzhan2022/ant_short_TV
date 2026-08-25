package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiInvocationContractTest {

    @Test
    void mapsCapabilitiesToModelRoutingServiceTypes() {
        assertThat(AiCapability.TEXT.modelServiceType()).isEqualTo("TEXT");
        assertThat(AiCapability.IMAGE.modelServiceType()).isEqualTo("IMAGE");
        assertThat(AiCapability.VIDEO_UNDERSTANDING.modelServiceType()).isEqualTo("VIDEO_UNDERSTANDING");
    }

    @Test
    void exposesStableBusinessSceneMetadata() {
        assertThat(AiBusinessScene.CHARACTER_EXTRACT.code()).isEqualTo("character_extract");
        assertThat(AiBusinessScene.CHARACTER_EXTRACT.capability()).isEqualTo(AiCapability.TEXT);
        assertThat(AiBusinessScene.CHARACTER_EXTRACT.pointScene()).isEqualTo("character_extract");
        assertThat(AiBusinessScene.CHARACTER_EXTRACT.promptTemplateId()).isEqualTo("script.element.character.extract");

        assertThat(AiBusinessScene.VIDEO_UNDERSTANDING.code()).isEqualTo("video_understanding");
        assertThat(AiBusinessScene.VIDEO_UNDERSTANDING.capability()).isEqualTo(AiCapability.VIDEO_UNDERSTANDING);
        assertThat(AiBusinessScene.VIDEO_SCRIPT_DRAFT.promptTemplateId()).isEqualTo("video.script.draft");
    }

    @Test
    void invocationRequestDefaultsCapabilityFromScene() {
        AiInvocationRequest request = AiInvocationRequest.text()
            .tenantId(1L)
            .userId(2L)
            .projectId(3L)
            .scene(AiBusinessScene.SCRIPT_GENERATE)
            .userPrompt("写一集短剧")
            .build();

        assertThat(request.capability()).isEqualTo(AiCapability.TEXT);
        assertThat(request.businessSceneCode()).isEqualTo("script_generate");
        assertThat(request.toAiContext()).isEqualTo(new AiContext(1L, 2L, 3L, null, null, "script_generate", null));
    }

    @Test
    void representsCompletedSynchronousAndAcceptedAsynchronousProviderOutcomes() {
        AiProviderExecutionOutcome<String> completed = AiProviderExecutionOutcome.completed(
            "final-result",
            "provider-request-1"
        );
        AiProviderExecutionOutcome<String> accepted = AiProviderExecutionOutcome.accepted(
            "provider-request-2",
            "external-task-2",
            Duration.ofSeconds(5),
            AiProviderReconciliationStatus.NOT_REQUIRED
        );

        assertThat(completed.outcome()).isEqualTo(AiProviderExecutionState.COMPLETED);
        assertThat(completed.response()).isEqualTo("final-result");
        assertThat(accepted.outcome()).isEqualTo(AiProviderExecutionState.ACCEPTED);
        assertThat(accepted.externalTaskId()).isEqualTo("external-task-2");
        assertThat(accepted.pollAfter()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void propagatesExecutionCorrelationIntoInvocationContext() {
        AiInvocationRequest request = AiInvocationRequest.text()
            .tenantId(11L)
            .userId(12L)
            .projectId(13L)
            .taskId(14L)
            .executionId(15L)
            .attemptId(16L)
            .executionVersion(2)
            .phase("SUBMIT")
            .idempotencyKey("execution-15-v2-submit")
            .traceId("trace-15")
            .scene(AiBusinessScene.SCRIPT_GENERATE)
            .userPrompt("生成")
            .build();

        assertThat(request.toAiContext()).isEqualTo(new AiContext(
            11L, 12L, 13L, 14L, null, "script_generate", "trace-15",
            15L, 16L, 2, "SUBMIT", "execution-15-v2-submit"
        ));
    }

    @Test
    void requiresStableIdempotencyForEveryProviderFacingPhase() {
        assertThatThrownBy(() -> new AiProviderPollingRequest(
            AiCapability.IMAGE,
            "external-task-1",
            null,
            15L,
            16L,
            2,
            "POLL"
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("idempotency key");
    }
}
