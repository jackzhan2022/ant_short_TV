package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.antshorttv.workflowagent.run.WorkflowAgentRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GlobalUnderstandingAgentAdapterTest {
    private final WorkflowAgentRunner runner = mock(WorkflowAgentRunner.class);
    private final ScriptGlobalUnderstandingRepository documents = mock(ScriptGlobalUnderstandingRepository.class);
    private final WorkflowAgentRunRepository runs = mock(WorkflowAgentRunRepository.class);

    @Test
    void invokesSavedAgentWithTrustedPipelineScopeAndReturnsCommittedDocument() throws Exception {
        GlobalUnderstandingAgentAdapter adapter = new GlobalUnderstandingAgentAdapter(runner, documents, runs, true);
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(41L);
        task.setTenantId(7L);
        task.setProjectId(8L);
        task.setScriptId(9L);
        task.setCreatedBy(10L);
        ScriptAnalysisStageEntity stage = new ScriptAnalysisStageEntity();
        stage.setId(42L);
        when(runner.runFormal(any())).thenReturn(new WorkflowAgentRunResult(99L, "{\"saved\":true}", List.of(
            new WorkflowAgentModelCall(100L, 11L, 12L, "request", "SUCCEEDED", "SUCCEEDED"))));
        var content = new ObjectMapper().readTree("{\"logline\":\"当前剧情\"}");
        when(documents.findCurrent(7L, 9L)).thenReturn(Optional.of(new ScriptGlobalUnderstandingDocument(
            1L, 7L, 8L, 9L, 1, content, "hash", 99L, 10L, 10L, null, null)));

        GlobalUnderstandingAgentAdapter.Execution result = adapter.execute(task, stage, null, 11L);

        ArgumentCaptor<WorkflowAgentRunInput> input = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner).runFormal(input.capture());
        assertThat(input.getValue().scriptId()).isEqualTo(9L);
        assertThat(input.getValue().taskId()).isEqualTo(41L);
        assertThat(input.getValue().analysisStageId()).isEqualTo(42L);
        assertThat(input.getValue().modelIdOverride()).isEqualTo(11L);
        assertThat(result.content()).isEqualTo(content);
        assertThat(result.modelCalls()).hasSize(1);
    }

    @Test
    void reconcilesCommittedDocumentWhenPostSaveRunAuditFails() throws Exception {
        GlobalUnderstandingAgentAdapter adapter = new GlobalUnderstandingAgentAdapter(runner, documents, runs, true);
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(41L);
        task.setTenantId(7L);
        task.setProjectId(8L);
        task.setScriptId(9L);
        task.setCreatedBy(10L);
        ScriptAnalysisStageEntity stage = new ScriptAnalysisStageEntity();
        stage.setId(42L);
        when(runner.runFormal(any())).thenThrow(new IllegalStateException("audit failed"));
        var content = new ObjectMapper().readTree("{\"logline\":\"已提交\"}");
        when(documents.findCurrent(7L, 9L)).thenReturn(Optional.of(new ScriptGlobalUnderstandingDocument(
            1L, 7L, 8L, 9L, 1, content, "hash", 99L, 10L, 10L, null, null)));
        when(runs.belongsToStage(99L, 7L, 41L, 42L)).thenReturn(true);
        when(documents.stageSucceeded(7L, 41L, 42L)).thenReturn(true);
        when(runs.modelCalls(99L, 7L)).thenReturn(List.of(
            new WorkflowAgentModelCall(100L, 11L, 12L, "request", "SUCCEEDED", "SUCCEEDED")));

        GlobalUnderstandingAgentAdapter.Execution result = adapter.execute(task, stage, null, 11L);

        assertThat(result.agentRunId()).isEqualTo(99L);
        assertThat(result.content()).isEqualTo(content);
        assertThat(result.modelCalls()).hasSize(1);
        verify(runs).reconcileCommitted(99L, "{\"saved\":true,\"reconciled\":true}");
    }
}
