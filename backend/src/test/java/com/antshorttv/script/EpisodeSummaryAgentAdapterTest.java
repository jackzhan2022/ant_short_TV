package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EpisodeSummaryAgentAdapterTest {
    private final WorkflowAgentRunner runner = mock(WorkflowAgentRunner.class);
    private final ScriptEpisodeSummaryRepository summaries = mock(ScriptEpisodeSummaryRepository.class);

    @Test
    void runsExactlyOneEpisodeWithoutGlobalUnderstandingAndRequiresCommittedSummary() throws Exception {
        EpisodeSummaryAgentAdapter adapter = new EpisodeSummaryAgentAdapter(runner, summaries, true);
        when(runner.runFormal(any(WorkflowAgentRunInput.class)))
            .thenReturn(new WorkflowAgentRunResult(77L, "{\"saved\":true}"));
        when(summaries.findCurrent(7L, 9L, 101L)).thenReturn(Optional.of(
            new ScriptEpisodeSummaryDocument(1L, 7L, 8L, 9L, 101L, 1,
                new ObjectMapper().readTree("{\"summary\":\"本集\",\"highlights\":[\"一\",\"二\"],\"endingHook\":null}"),
                "AI", 77L, 10L, 10L, null, null)));

        EpisodeSummaryAgentAdapter.Execution result = adapter.executeChild(
            task(), stage(), 101L, null, 11L);

        ArgumentCaptor<WorkflowAgentRunInput> input = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner).runFormal(input.capture());
        assertThat(input.getValue().episodeId()).isEqualTo(101L);
        assertThat(input.getValue().scriptId()).isEqualTo(9L);
        assertThat(input.getValue().input()).doesNotContain("全局理解");
        assertThat(result.agentRunId()).isEqualTo(77L);
    }

    @Test
    void rejectsFinalTextWithoutCurrentFormalSave() {
        EpisodeSummaryAgentAdapter adapter = new EpisodeSummaryAgentAdapter(runner, summaries, true);
        when(runner.runFormal(any(WorkflowAgentRunInput.class)))
            .thenReturn(new WorkflowAgentRunResult(77L, "概要文本"));
        when(summaries.findCurrent(7L, 9L, 101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.executeChild(task(), stage(), 101L, null, 11L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("正式概要");
    }

    private ScriptAnalysisTaskEntity task() {
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(41L);
        task.setTenantId(7L);
        task.setProjectId(8L);
        task.setScriptId(9L);
        task.setCreatedBy(10L);
        return task;
    }

    private ScriptAnalysisStageEntity stage() {
        ScriptAnalysisStageEntity stage = new ScriptAnalysisStageEntity();
        stage.setId(42L);
        stage.setStageCode("EPISODE_SUMMARY");
        return stage;
    }
}
