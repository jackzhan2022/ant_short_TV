package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.points.TeamPointService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScriptAnalysisExecutionServiceTest {

    @Mock
    private ScriptAnalysisTaskMapper taskMapper;

    @Mock
    private ScriptAnalysisStageMapper stageMapper;

    @Mock
    private ScriptAnalysisResultMapper resultMapper;

    @Mock
    private ScriptMapper scriptMapper;

    @Mock
    private ScriptVersionMapper versionMapper;

    @Mock
    private ScriptElementDraftService scriptElementDraftService;

    @Mock
    private AiInvocationService aiInvocationService;

    @Mock
    private ProjectAiConfigService projectAiConfigService;

    @Mock
    private TeamPointService teamPointService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ScriptAnalysisExecutionService service;

    @BeforeEach
    void setUp() {
        service = new ScriptAnalysisExecutionService(
            taskMapper,
            stageMapper,
            resultMapper,
            scriptMapper,
            versionMapper,
            scriptElementDraftService,
            aiInvocationService,
            projectAiConfigService,
            teamPointService,
            objectMapper
        );
    }

    @Test
    void usesParserOutputForExplicitEpisodeHeadings() throws Exception {
        ScriptAnalysisTaskEntity task = task(1L, 2L, 3L);
        ScriptVersionEntity version = version(4L, """
            第1集：回家
            主角回到故乡。

            第2集：冲突
            反派出现。
            """);

        String json = invokeString("splitEpisodes", task, version);

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.path("episodes")).hasSize(2);
        assertThat(root.path("episodes").get(0).path("title").asText()).isEqualTo("第1集：回家");
        verify(aiInvocationService, times(0)).invokeText(any());
    }

    @Test
    void fallsBackToAiWhenThereAreNoEpisodeHeadings() throws Exception {
        ScriptAnalysisTaskEntity task = task(1L, 2L, 3L);
        ScriptVersionEntity version = version(4L, "一段没有标题的长正文，系统需要智能拆分。");
        when(projectAiConfigService.resolveModelId(2L, 3L, "TEXT")).thenReturn(7L);
        when(teamPointService.consumeForAi(any(), anyInt(), any(), any(), any())).thenReturn(1L);
        when(aiInvocationService.invokeText(any())).thenAnswer(invocation -> {
            String raw = """
                {"episodes":[{"episodeNo":1,"title":"第1集","content":"智能拆分正文"}]}
                """;
            return AiInvocationResult.text(
                AiBusinessScene.SCRIPT_EPISODE_SPLIT.code(),
                new AiTextResponse(raw, "req-split", 11, 22, 33, 44L, Map.of()),
                88L,
                null
            );
        });

        String json = invokeString("splitEpisodes", task, version);

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.path("episodes")).hasSize(1);
        assertThat(root.path("episodes").get(0).path("content").asText()).isEqualTo("智能拆分正文");
        verify(aiInvocationService).invokeText(any());
    }

    @Test
    void rejectsNonContinuousEpisodeNumbersDuringValidation() throws Exception {
        JsonNode invalid = objectMapper.readTree("""
            {"episodes":[{"episodeNo":1,"content":"A"},{"episodeNo":3,"content":"B"}]}
            """);

        assertThatThrownBy(() -> invokeVoid("validateStageResult", "EPISODE_SPLITTING", invalid, "A\nB"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("编号必须从1连续递增");
    }

    @Test
    void acceptsEpisodeContentThatCoversAHeadedSourceWithoutRepeatingHeadings() throws Exception {
        JsonNode result = objectMapper.readTree("""
            {"episodes":[
              {"episodeNo":1,"content":"主角回到故乡。"},
              {"episodeNo":2,"content":"新的冲突出现。"}
            ]}
            """);

        invokeVoid(
            "validateStageResult",
            "EPISODE_SPLITTING",
            result,
            "第1集\n主角回到故乡。\n第2集\n新的冲突出现。"
        );
    }

    @Test
    void normalizesRecognizedAssetsFromStructuredAnalysis() throws Exception {
        JsonNode parsed = objectMapper.readTree("""
            {
              "characters":[{"name":" 林晚 ","roleType":"LEAD","gender":"女","ageRange":"25-30","identity":"回归千金","personality":["冷静","","果断"],"appearance":"黑色风衣","prompt":"角色提示"}],
              "scenes":[{"name":"林家老宅门口","sceneType":"EXTERIOR","atmosphere":"雨夜","description":"门口","visualStyle":"电影感","prompt":"场景提示"}],
              "props":[{"name":"录音笔","propType":"KEY_PROP","appearance":"银色","plotFunction":"证据","relatedCharacter":"林晚","prompt":"道具提示"}]
            }
            """);

        ScriptElementExtractionResult result = invoke("normalizeExtractionResult", parsed);

        assertThat(result.elementType()).isEqualTo(ScriptElementType.ALL);
        assertThat(result.characters()).hasSize(1);
        assertThat(result.characters().get(0).name()).isEqualTo("林晚");
        assertThat(result.characters().get(0).personality()).containsExactly("冷静", "果断");
        assertThat(result.scenes()).extracting(ScriptElementExtractionResult.SceneElement::name)
            .containsExactly("林家老宅门口");
        assertThat(result.props()).extracting(ScriptElementExtractionResult.PropElement::name)
            .containsExactly("录音笔");
    }

    @Test
    void summarizesEpisodesFromPriorSplitResults() throws Exception {
        ScriptAnalysisTaskEntity task = task(9L, 2L, 3L);
        ScriptVersionEntity version = version(4L, "第1集\n主角回家。\n第2集\n反派出现。");
        ScriptAnalysisStageEntity splitStage = stage(21L, "EPISODE_SPLITTING", 2, "SUCCEEDED");
        ScriptAnalysisResultEntity splitResult = new ScriptAnalysisResultEntity();
        splitResult.setStageId(21L);
        splitResult.setStatus("SUCCEEDED");
        splitResult.setNormalizedJson("""
            {"episodes":[{"episodeNo":1,"title":"第1集","content":"主角回家。"},{"episodeNo":2,"title":"第2集","content":"反派出现。"}]}
            """);
        when(stageMapper.selectByTask(9L)).thenReturn(List.of(splitStage));
        when(resultMapper.selectLatestByStage(21L)).thenReturn(splitResult);
        when(projectAiConfigService.resolveModelId(2L, 3L, "TEXT")).thenReturn(7L);
        when(teamPointService.consumeForAi(any(), anyInt(), any(), any(), any())).thenReturn(1L);
        when(aiInvocationService.invokeText(any())).thenAnswer(invocation -> {
            AiInvocationRequest request = invocation.getArgument(0);
            Object episodesVariable = request.templateVariables().get("episodes");
            JsonNode episodes = episodesVariable instanceof JsonNode node
                ? node
                : objectMapper.readTree((String) episodesVariable);
            JsonNode episode = episodes.get(0);
            int episodeNo = episode.path("episodeNo").asInt();
            String raw = """
                {"episodes":[{"episodeNo":%d,"summary":"第%d集概要","highlights":["冲突%d"],"endingHook":"钩子%d"}]}
                """.formatted(episodeNo, episodeNo, episodeNo, episodeNo);
            return AiInvocationResult.text(
                AiBusinessScene.SCRIPT_EPISODE_SUMMARY.code(),
                new AiTextResponse(raw, "summary-%d".formatted(episodeNo), 12, 34, 46, 55L, Map.of()),
                99L + episodeNo,
                null
            );
        });

        Object summaryCall = invoke("summarizeEpisodes", task, version);
        Method normalizedJsonMethod = summaryCall.getClass().getDeclaredMethod("normalizedJson");
        normalizedJsonMethod.setAccessible(true);
        String normalizedJson = (String) normalizedJsonMethod.invoke(summaryCall);
        JsonNode root = objectMapper.readTree(normalizedJson);

        assertThat(root.path("episodes")).hasSize(2);
        assertThat(root.path("episodes").get(0).path("summary").asText()).isEqualTo("第1集概要");
        assertThat(root.path("episodes").get(1).path("endingHook").asText()).isEqualTo("钩子2");
        verify(aiInvocationService, times(2)).invokeText(any());
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(String methodName, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i].getClass();
        }
        Method method = findMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return (T) method.invoke(service, args);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private String invokeString(String methodName, Object... args) throws Exception {
        return invoke(methodName, args);
    }

    private void invokeVoid(String methodName, Object... args) throws Exception {
        invoke(methodName, args);
    }

    private Method findMethod(String methodName, Class<?>[] parameterTypes) throws NoSuchMethodException {
        for (Method method : ScriptAnalysisExecutionService.class.getDeclaredMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != parameterTypes.length) {
                continue;
            }
            Class<?>[] declaredTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < declaredTypes.length; i++) {
                if (parameterTypes[i] != null && !declaredTypes[i].isAssignableFrom(parameterTypes[i])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return method;
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private ScriptAnalysisTaskEntity task(Long id, Long tenantId, Long projectId) {
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(id);
        task.setTenantId(tenantId);
        task.setProjectId(projectId);
        task.setScriptId(5L);
        task.setScriptVersionId(6L);
        task.setCreatedBy(7L);
        return task;
    }

    private ScriptVersionEntity version(Long id, String content) {
        ScriptVersionEntity version = new ScriptVersionEntity();
        version.setId(id);
        version.setContent(content);
        return version;
    }

    private ScriptAnalysisStageEntity stage(Long id, String code, int order, String status) {
        ScriptAnalysisStageEntity stage = new ScriptAnalysisStageEntity();
        stage.setId(id);
        stage.setStageCode(code);
        stage.setStageOrder(order);
        stage.setStatus(status);
        return stage;
    }
}
