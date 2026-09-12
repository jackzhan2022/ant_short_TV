package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.execution.AiExecutionClaim;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionClaimLostException;
import com.antshorttv.execution.AiExecutionClaimService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    private ProjectAiConfigService projectAiConfigService;

    @Mock
    private ScriptAnalysisConfigSnapshotService configSnapshotService;

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
            projectAiConfigService
        );
        ReflectionTestUtils.setField(service, "configSnapshotService", configSnapshotService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GLOBAL_UNDERSTANDING", "EPISODE_SPLITTING", "EPISODE_SUMMARY", "CHARACTER_SCENE_RECOGNITION"})
    void missingWorkflowAgentFailsBeforeResolvingAModel(String stageCode) {
        ScriptAnalysisTaskEntity task = task(91L, 2L, 3L);
        ScriptEntity script = new ScriptEntity();
        script.setCurrentVersionId(6L);
        ScriptAnalysisStageEntity stage = stage(1L, stageCode, 1, "PENDING");
        when(taskMapper.selectById(91L)).thenReturn(task);
        when(versionMapper.selectById(6L)).thenReturn(version(6L, "剧本"));
        when(scriptMapper.selectById(5L)).thenReturn(script);
        when(stageMapper.selectByTask(91L)).thenReturn(List.of(stage));

        assertThatThrownBy(() -> service.executeTask(91L))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("Workflow Agent");
        assertThat(stage.getStatus()).isEqualTo("FAILED");
        org.mockito.Mockito.verifyNoInteractions(projectAiConfigService);
    }

    @Test
    void upstreamGlobalFailurePreventsSplitAndEpisodeBranches() {
        var task = task(91L, 2L, 3L);
        var script = new ScriptEntity();
        script.setCurrentVersionId(6L);
        var stages = List.of(stage(1L, "GLOBAL_UNDERSTANDING", 1, "PENDING"),
            stage(2L, "EPISODE_SPLITTING", 2, "PENDING"),
            stage(3L, "EPISODE_SUMMARY", 3, "PENDING"),
            stage(4L, "CHARACTER_SCENE_RECOGNITION", 4, "PENDING"));
        when(taskMapper.selectById(91L)).thenReturn(task);
        when(versionMapper.selectById(6L)).thenReturn(version(6L, "剧本"));
        when(scriptMapper.selectById(5L)).thenReturn(script);
        when(stageMapper.selectByTask(91L)).thenReturn(stages);
        var global = mock(GlobalUnderstandingAgentAdapter.class);
        var fanout = mock(EpisodeFanoutCoordinator.class);
        when(global.execute(any(), any(), any(), any())).thenThrow(new IllegalStateException("global failed"));
        ReflectionTestUtils.setField(service, "globalUnderstandingAgentAdapter", global);
        ReflectionTestUtils.setField(service, "episodeFanoutCoordinator", fanout);

        assertThatThrownBy(() -> service.executeTask(91L)).hasMessageContaining("global failed");
        org.mockito.Mockito.verifyNoInteractions(fanout);
        assertThat(stages.get(1).getStatus()).isEqualTo("PENDING");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preparesBothBranchesAndRecognitionCompletesWhileSummaryIsBlocked(boolean summaryFails) throws Exception {
        ScriptAnalysisTaskEntity task = task(91L, 2L, 3L);
        ScriptEntity script = new ScriptEntity();
        script.setCurrentVersionId(6L);
        var summaryStage = stage(3L, "EPISODE_SUMMARY", 3, "PENDING");
        var recognitionStage = stage(4L, "CHARACTER_SCENE_RECOGNITION", 4, "PENDING");
        when(taskMapper.selectById(91L)).thenReturn(task);
        when(versionMapper.selectById(6L)).thenReturn(version(6L, "剧本"));
        when(scriptMapper.selectById(5L)).thenReturn(script);
        when(stageMapper.selectByTask(91L)).thenReturn(List.of(summaryStage, recognitionStage));
        when(configSnapshotService.modelIdFor(91L)).thenReturn(99L);
        var store = mock(EpisodeFanoutStore.class);
        var runner = mock(com.antshorttv.workflowagent.run.WorkflowAgentRunner.class);
        var plan = mock(com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan.class);
        when(runner.freezeFormal(anyString())).thenReturn(plan);
        var episodes = List.of(new EpisodeFanoutCoordinator.EpisodeUnit(100L, "e1", "fp", "ACTIVE"));
        when(store.currentEpisodes(2L, 3L, 5L)).thenReturn(episodes);
        var prepared = new java.util.concurrent.atomic.AtomicInteger();
        when(store.openSnapshot(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
            .thenAnswer(invocation -> {
                prepared.incrementAndGet();
                return ((ScriptAnalysisStageEntity) invocation.getArgument(1)).getId();
            });
        when(store.runnableUnits(org.mockito.ArgumentMatchers.anyLong())).thenReturn(episodes);
        when(store.markRunning(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
        var failed = new java.util.concurrent.atomic.AtomicBoolean();
        if (summaryFails) doAnswer(invocation -> { failed.set(true); return null; }).when(store).markFailed(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyInt(), anyString(), anyString());
        when(store.progress(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(invocation ->
            invocation.getArgument(0).equals(3L) && failed.get()
                ? new EpisodeFanoutCoordinator.Progress(1, 0, 1, 0, 0, "PARTIAL_FAILED")
                : new EpisodeFanoutCoordinator.Progress(1, 1, 0, 0, 0, "SUCCEEDED"));
        when(store.snapshotMatches(org.mockito.ArgumentMatchers.anyLong(), any())).thenReturn(true);
        var recognitionFinished = new java.util.concurrent.CountDownLatch(1);
        var summary = mock(EpisodeSummaryAgentAdapter.class);
        var recognition = mock(AssetRecognitionAgentAdapter.class);
        when(summary.executeClaimedChild(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            assertThat(prepared.get()).isEqualTo(2);
            assertThat(recognitionFinished.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            if (summaryFails) throw new IllegalStateException("summary failed");
            return new EpisodeSummaryAgentAdapter.Execution(null, 13L, List.of());
        });
        when(recognition.executeClaimedChild(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            assertThat(prepared.get()).isEqualTo(2);
            return new AssetRecognitionAgentAdapter.Execution(14L, List.of(
                new com.antshorttv.workflowagent.run.WorkflowAgentModelCall(501L, 99L, 41L,
                    "request-501", "SUCCEEDED", "SUCCEEDED")));
        });
        long owner = Thread.currentThread().getId();
        doAnswer(invocation -> {
            assertThat(Thread.currentThread().getId()).isEqualTo(owner);
            return 1;
        }).when(taskMapper).updateById(any(ScriptAnalysisTaskEntity.class));
        doAnswer(invocation -> {
            var stage = (ScriptAnalysisStageEntity) invocation.getArgument(0);
            if (stage.getId().equals(4L) && "SUCCEEDED".equals(stage.getStatus())) recognitionFinished.countDown();
            return 1;
        }).when(stageMapper).updateById(any(ScriptAnalysisStageEntity.class));
        ReflectionTestUtils.setField(service, "episodeSummaryAgentAdapter", summary);
        ReflectionTestUtils.setField(service, "assetRecognitionAgentAdapter", recognition);
        ReflectionTestUtils.setField(service, "assetRecognitionFinalizer", mock(AssetRecognitionFinalizer.class));
        ReflectionTestUtils.setField(service, "episodeFanoutCoordinator", new EpisodeFanoutCoordinator(store, runner, 1));

        if (summaryFails) {
            assertThatThrownBy(() -> service.executeTask(91L)).isInstanceOf(IllegalStateException.class);
        } else {
            var outcome = service.executeTask(91L, null);
            assertThat(outcome.providerCallCount()).isEqualTo(1);
            assertThat(outcome.lastCallLogId()).isEqualTo(501L);
        }

        assertThat(task.getStatus()).isEqualTo(summaryFails ? "FAILED" : "COMPLETED");
        assertThat(summaryStage.getStatus()).isEqualTo(summaryFails ? "FAILED" : "SUCCEEDED");
        assertThat(recognitionStage.getStatus()).isEqualTo("SUCCEEDED");
        if (summaryFails) {
            org.mockito.Mockito.reset(summary);
            when(summary.executeClaimedChild(any(), any(), any(), any(), any(), any()))
                .thenReturn(new EpisodeSummaryAgentAdapter.Execution(null, 13L, List.of()));
            failed.set(false);
            service.executeTask(91L);
            verify(recognition, times(1)).executeClaimedChild(any(), any(), any(), any(), any(), any());
            assertThat(prepared.get()).isEqualTo(3);
            assertThat(task.getStatus()).isEqualTo("COMPLETED");
        }
    }

    @Test
    void staleScriptVersionStopsBeforeStartingAnyAgent() {
        ScriptAnalysisTaskEntity task = task(91L, 2L, 3L);
        ScriptEntity script = new ScriptEntity();
        script.setCurrentVersionId(8L);
        ScriptAnalysisStageEntity stage = stage(1L, "GLOBAL_UNDERSTANDING", 1, "PENDING");
        when(taskMapper.selectById(91L)).thenReturn(task);
        when(versionMapper.selectById(6L)).thenReturn(version(6L, "旧剧本"));
        when(scriptMapper.selectById(5L)).thenReturn(script);
        when(stageMapper.selectByTask(91L)).thenReturn(List.of(stage));
        GlobalUnderstandingAgentAdapter global = mock(GlobalUnderstandingAgentAdapter.class);
        ReflectionTestUtils.setField(service, "globalUnderstandingAgentAdapter", global);

        assertThatThrownBy(() -> service.executeTask(91L)).isInstanceOf(IllegalStateException.class);
        assertThat(stage.getErrorCode()).isEqualTo("STALE_SCRIPT_VERSION");
        assertThat(stage.getRetryable()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(global, projectAiConfigService);
    }

    @Test
    void staleAttemptCannotPublishScriptAnalysisFailureAfterOwnershipLoss() {
        ScriptAnalysisTaskEntity task = task(91L, 2L, 3L);
        task.setStatus("PENDING");
        ScriptVersionEntity version = version(6L, "当前剧本");
        ScriptEntity script = new ScriptEntity();
        script.setId(5L);
        script.setCurrentVersionId(6L);
        ScriptAnalysisStageEntity stage = stage(1L, "GLOBAL_UNDERSTANDING", 1, "PENDING");
        when(taskMapper.selectById(91L)).thenReturn(task);
        when(versionMapper.selectById(6L)).thenReturn(version);
        when(stageMapper.selectByTask(91L)).thenReturn(List.of(stage));
        when(scriptMapper.selectById(5L)).thenReturn(script);

        GlobalUnderstandingAgentAdapter global = mock(GlobalUnderstandingAgentAdapter.class);
        when(global.execute(any(), any(), any(), any()))
            .thenThrow(new IllegalStateException("late stale failure"));
        ReflectionTestUtils.setField(service, "globalUnderstandingAgentAdapter", global);

        AiExecutionClaimService claims = mock(AiExecutionClaimService.class);
        ReflectionTestUtils.setField(service, "executionClaimService", claims);
        AiExecutionTaskEntity execution = new AiExecutionTaskEntity();
        execution.id = 700L;
        execution.executionVersion = 1;
        AiExecutionClaim claim = new AiExecutionClaim(700L, 701L, "old-token", 1, "SUBMIT");
        doNothing().doNothing().doThrow(new AiExecutionClaimLostException(700L))
            .when(claims).requireActive(claim);

        assertThatThrownBy(() -> service.executeTask(91L, new AiExecutionContext(execution, claim)))
            .isInstanceOf(AiExecutionClaimLostException.class);

        verify(resultMapper, never()).insert(any(ScriptAnalysisResultEntity.class));
        verify(taskMapper, times(2)).updateById(task);
        assertThat(task.getErrorCode()).isNull();
        assertThat(task.getErrorMessage()).isNull();
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
