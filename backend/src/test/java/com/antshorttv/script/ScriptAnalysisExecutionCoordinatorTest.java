package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.execution.AiExecutionResponse;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScriptAnalysisExecutionCoordinatorTest {
    @Test
    void createsANewReservedExecutionWhenRetryingACanceledAnalysis() {
        ScriptAnalysisTaskMapper taskMapper = mock(ScriptAnalysisTaskMapper.class);
        ScriptVersionMapper versionMapper = mock(ScriptVersionMapper.class);
        ProjectAiConfigService projectAiConfig = mock(ProjectAiConfigService.class);
        AiExecutionService executionService = mock(AiExecutionService.class);
        AiExecutionResponseMapper responseMapper = mock(AiExecutionResponseMapper.class);
        ScriptAnalysisExecutionCoordinator coordinator = new ScriptAnalysisExecutionCoordinator(
            taskMapper, versionMapper, projectAiConfig, executionService, responseMapper);
        var modelLookup = mock(com.antshorttv.workflowagent.agent.WorkflowAgentModelLookup.class);
        ReflectionTestUtils.setField(coordinator, "workflowAgentModelLookup", modelLookup);

        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(22L);
        task.setTenantId(4L);
        task.setProjectId(27L);
        task.setScriptVersionId(18L);
        task.setExecutionId(79014L);
        task.setIdempotencyKey("script-analysis-task-22");
        AiExecutionTaskEntity canceled = new AiExecutionTaskEntity();
        canceled.id = 79014L;
        canceled.status = "CANCELED";
        AiExecutionTaskEntity restarted = new AiExecutionTaskEntity();
        restarted.id = 79015L;
        ScriptVersionEntity version = new ScriptVersionEntity();
        version.setContent("script");
        AiExecutionResponse response = mock(AiExecutionResponse.class);

        when(executionService.requireTask(79014L)).thenReturn(canceled);
        when(versionMapper.selectById(18L)).thenReturn(version);
        var snapshots = mock(ScriptAnalysisConfigSnapshotService.class);
        ReflectionTestUtils.setField(coordinator, "configSnapshotService", snapshots);
        when(snapshots.modelIdFor(22L)).thenReturn(7L);
        when(executionService.restartCanceledWithReservation(
            eq(79014L), eq(22L), eq(7L), contains("79014"), contains("79014"), anyMap(), anyMap()
        )).thenReturn(restarted);
        when(responseMapper.toResponse(restarted)).thenReturn(response);

        assertThat(coordinator.retry(task)).isSameAs(response);
        verify(modelLookup).requireEnabledTextModel(7L);
        org.mockito.Mockito.verifyNoInteractions(projectAiConfig);
        ArgumentCaptor<ScriptAnalysisTaskEntity> saved = ArgumentCaptor.forClass(ScriptAnalysisTaskEntity.class);
        verify(taskMapper).updateById(saved.capture());
        assertThat(saved.getValue().getExecutionId()).isEqualTo(79015L);
    }

    @Test
    void reservesOneAdditionalCallForTheTwoRoundGlobalAgent() {
        ScriptAnalysisExecutionCoordinator coordinator = new ScriptAnalysisExecutionCoordinator(
            mock(ScriptAnalysisTaskMapper.class), mock(ScriptVersionMapper.class),
            mock(ProjectAiConfigService.class), mock(AiExecutionService.class),
            mock(AiExecutionResponseMapper.class));

        assertThat(coordinator.maximumCallCount("script")).isEqualTo(5);
    }
}
