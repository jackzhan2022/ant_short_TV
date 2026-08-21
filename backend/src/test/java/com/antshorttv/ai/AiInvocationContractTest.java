package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;

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
}
