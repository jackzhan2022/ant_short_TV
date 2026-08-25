package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.execution.AiExecutionAttemptMapper;
import com.antshorttv.points.TeamPointService;
import com.antshorttv.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScriptElementExtractionServiceTest {

    private final ProjectAiConfigService projectAiConfigService = mock(ProjectAiConfigService.class);
    private final AiInvocationService aiInvocationService = mock(AiInvocationService.class);
    private final TeamPointService teamPointService = mock(TeamPointService.class);
    private final ScriptElementExtractionService extractionService = new ScriptElementExtractionService(
        projectAiConfigService,
        aiInvocationService,
        teamPointService,
        new ObjectMapper(),
        mock(AiExecutionAttemptMapper.class)
    );

    @Test
    void extractsCharactersScenesAndPropsIntoTypedResults() {
        TenantContext context = new TenantContext(301L, 201L, 401L, "OWNER");
        ScriptEntity script = script("AI解析短剧", "林晚在天台拿出录音笔，证明反派篡改遗嘱。");
        when(projectAiConfigService.resolveModelId(201L, 101L, "TEXT")).thenReturn(501L);
        when(teamPointService.consumeForAi(any(TenantContext.class), anyInt(), any(String.class), any(), any(String.class)))
            .thenReturn(1L);
        when(aiInvocationService.invokeText(any(AiInvocationRequest.class))).thenAnswer(invocation -> {
            AiInvocationRequest aiRequest = invocation.getArgument(0);
            String content = switch (aiRequest.businessSceneCode()) {
                case "character_extract" -> """
                    {"characters":[{"name":"林晚","roleType":"LEAD","gender":"女","ageRange":"25-30","identity":"回归千金","personality":["冷静","果断"],"appearance":"黑色风衣","prompt":"林晚角色定妆照"}]}
                    """;
                case "scene_extract" -> """
                    {"scenes":[{"name":"天台","sceneType":"EXTERIOR","atmosphere":"深夜冷风","description":"城市高楼天台","visualStyle":"冷色电影感","prompt":"深夜天台"}]}
                    """;
                case "prop_extract" -> """
                    {"props":[{"name":"录音笔","propType":"KEY_PROP","appearance":"银色小型录音笔","plotFunction":"证明遗嘱被篡改","prompt":"录音笔特写"}]}
                    """;
                default -> throw new IllegalArgumentException(aiRequest.businessSceneCode());
            };
            AiTextResponse response = new AiTextResponse(content, null, null, null, null, 1L, null);
            return AiInvocationResult.text(aiRequest.businessSceneCode(), response, 1L, null);
        });

        ScriptElementExtractionResult result = extractionService.extract(context, 101L, script, ScriptElementType.ALL);

        assertThat(result.characters()).extracting(ScriptElementExtractionResult.CharacterElement::name)
            .containsExactly("林晚");
        assertThat(result.scenes()).extracting(ScriptElementExtractionResult.SceneElement::name)
            .containsExactly("天台");
        assertThat(result.props()).extracting(ScriptElementExtractionResult.PropElement::name)
            .containsExactly("录音笔");
        ArgumentCaptor<AiInvocationRequest> requestCaptor = ArgumentCaptor.forClass(AiInvocationRequest.class);
        verify(aiInvocationService, org.mockito.Mockito.times(3)).invokeText(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues()).allSatisfy(request ->
            assertThat(request.templateVariables()).containsEntry("scriptTitle", "AI解析短剧")
                .containsEntry("scriptContent", script.getContent())
        );
        assertThat(requestCaptor.getAllValues()).extracting(AiInvocationRequest::scene)
            .containsExactly(AiBusinessScene.CHARACTER_EXTRACT, AiBusinessScene.SCENE_EXTRACT, AiBusinessScene.PROP_EXTRACT);
        assertThat(requestCaptor.getAllValues()).extracting(AiInvocationRequest::agentCode)
            .containsExactly("script-character-extract", "script-scene-extract", "script-prop-extract");
    }

    private ScriptEntity script(String title, String content) {
        ScriptEntity script = new ScriptEntity();
        script.setTitle(title);
        script.setContent(content);
        return script;
    }
}
