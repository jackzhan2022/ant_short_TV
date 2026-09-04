package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.execution.AiExecutionClaim;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.antshorttv.workflowagent.tool.StoryboardToolDataService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StoryboardAgentAdapterTest {
    private final WorkflowAgentRunner runner = mock(WorkflowAgentRunner.class);
    private final StoryboardToolDataService storyboards = mock(StoryboardToolDataService.class);

    @Test
    void runsOneEpisodeWithTrustedExecutionScopeAndFrozenTextModel() {
        StoryboardAgentAdapter adapter = new StoryboardAgentAdapter(runner, storyboards, true);
        ScriptAiOperationEntity operation = operation();
        AiExecutionContext execution = execution();
        WorkflowAgentModelCall call = new WorkflowAgentModelCall(
            901L, 801L, 701L, "provider-request", "SUCCEEDED", "SUCCEEDED");
        when(runner.runFormal(any())).thenReturn(
            new WorkflowAgentRunResult(601L, "{\"saved\":true}", List.of(call)));
        when(storyboards.hasCompleteRunSet(11L, 22L, 44L, 601L)).thenReturn(true);

        StoryboardAgentAdapter.Execution result = adapter.execute(operation, 44L, execution);

        ArgumentCaptor<WorkflowAgentRunInput> input = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner).runFormal(input.capture());
        assertThat(input.getValue().tenantId()).isEqualTo(11L);
        assertThat(input.getValue().projectId()).isEqualTo(22L);
        assertThat(input.getValue().scriptId()).isEqualTo(33L);
        assertThat(input.getValue().episodeId()).isEqualTo(44L);
        assertThat(input.getValue().taskId()).isEqualTo(55L);
        assertThat(input.getValue().executionId()).isEqualTo(66L);
        assertThat(input.getValue().attemptId()).isEqualTo(77L);
        assertThat(input.getValue().executionVersion()).isEqualTo(3);
        assertThat(input.getValue().modelIdOverride()).isEqualTo(88L);
        assertThat(result.agentRunId()).isEqualTo(601L);
        assertThat(result.modelCalls()).containsExactly(call);
    }

    @Test
    void rejectsACompletedRunWithoutItsCompleteCommittedStoryboardSet() {
        StoryboardAgentAdapter adapter = new StoryboardAgentAdapter(runner, storyboards, true);
        when(runner.runFormal(any())).thenReturn(
            new WorkflowAgentRunResult(601L, "model text only", List.of()));
        when(storyboards.hasCompleteRunSet(11L, 22L, 44L, 601L)).thenReturn(false);

        assertThatThrownBy(() -> adapter.execute(operation(), 44L, execution()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("完整正式分镜");
    }

    private ScriptAiOperationEntity operation() {
        ScriptAiOperationEntity operation = new ScriptAiOperationEntity();
        operation.id = 55L;
        operation.tenantId = 11L;
        operation.projectId = 22L;
        operation.scriptId = 33L;
        operation.createdBy = 99L;
        return operation;
    }

    private AiExecutionContext execution() {
        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.id = 66L;
        task.requestedModelId = 87L;
        task.resolvedModelId = 88L;
        task.executionVersion = 3;
        return new AiExecutionContext(task, new AiExecutionClaim(66L, 77L, "claim", 3, "SUBMIT"));
    }
}
