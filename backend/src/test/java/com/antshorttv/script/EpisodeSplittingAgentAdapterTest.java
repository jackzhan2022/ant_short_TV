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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EpisodeSplittingAgentAdapterTest {
    private final WorkflowAgentRunner runner = mock(WorkflowAgentRunner.class);
    private final ScriptEpisodeService episodes = mock(ScriptEpisodeService.class);

    @Test
    void alwaysInvokesTheAgentAndReturnsOnlyItsCommittedFormalEpisodes() {
        EpisodeSplittingAgentAdapter adapter = new EpisodeSplittingAgentAdapter(runner, episodes, true);
        ScriptAnalysisTaskEntity task = task();
        ScriptAnalysisStageEntity stage = stage();
        when(runner.runFormal(any())).thenReturn(new WorkflowAgentRunResult(99L, "{\"saved\":true}"));
        when(episodes.currentEpisodes(7L, 8L, 9L)).thenReturn(List.of(
            new ScriptEpisodeResponse(101L, 1, "第1集", "第1集\nA", null, "fp", 99L, null),
            new ScriptEpisodeResponse(102L, 2, "第2集", "第2集\nB", null, "fp2", 99L, null)));

        EpisodeSplittingAgentAdapter.Execution result = adapter.execute(task, stage, null, 11L);

        ArgumentCaptor<WorkflowAgentRunInput> input = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner).runFormal(input.capture());
        assertThat(input.getValue().agentCode()).isEqualTo("short-drama-episode-splitting");
        assertThat(input.getValue().scriptId()).isEqualTo(9L);
        assertThat(input.getValue().episodeId()).isNull();
        assertThat(result.episodes()).hasSize(2);
        assertThat(result.agentRunId()).isEqualTo(99L);
    }

    @Test
    void doesNotSucceedWhenTerminalSaveDidNotCommitFormalCoverage() {
        EpisodeSplittingAgentAdapter adapter = new EpisodeSplittingAgentAdapter(runner, episodes, true);
        when(runner.runFormal(any())).thenReturn(new WorkflowAgentRunResult(99L, "final text only"));
        when(episodes.currentEpisodes(7L, 8L, 9L)).thenReturn(List.of());

        assertThatThrownBy(() -> adapter.execute(task(), stage(), null, 11L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("正式剧集");
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
        stage.setStageCode("EPISODE_SPLITTING");
        return stage;
    }
}
