package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.workflowagent.agent.AssetRecognitionAgentBootstrap;
import com.antshorttv.workflowagent.agent.WorkflowAgentRecord;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssetRecognitionAgentAdapterTest {
    private final WorkflowAgentRunner runner = mock(WorkflowAgentRunner.class);
    private final EpisodeAssetPersistenceService assets = mock(EpisodeAssetPersistenceService.class);

    @Test
    void runsOneEpisodeAgainstFrozenPlanAndRequiresFormalCoverage() {
        AssetRecognitionAgentAdapter adapter = new AssetRecognitionAgentAdapter(runner, assets, true);
        WorkflowAgentExecutionPlan plan = plan();
        when(runner.runFormal(any(WorkflowAgentExecutionPlan.class), any(WorkflowAgentRunInput.class)))
            .thenReturn(new WorkflowAgentRunResult(77L, "{\"saved\":true}"));
        when(assets.hasCoverage(7L, 9L, 101L, 77L)).thenReturn(true);

        AssetRecognitionAgentAdapter.Execution result = adapter.executeChild(
            plan, task(), stage(), 101L, null);

        ArgumentCaptor<WorkflowAgentRunInput> input = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner).runFormal(any(WorkflowAgentExecutionPlan.class), input.capture());
        assertThat(input.getValue().episodeId()).isEqualTo(101L);
        assertThat(input.getValue().input()).doesNotContain("概要", "全局理解");
        assertThat(result.agentRunId()).isEqualTo(77L);
    }

    @Test
    void rejectsModelTextWithoutTerminalFormalSave() {
        AssetRecognitionAgentAdapter adapter = new AssetRecognitionAgentAdapter(runner, assets, true);
        when(runner.runFormal(any(WorkflowAgentExecutionPlan.class), any(WorkflowAgentRunInput.class)))
            .thenReturn(new WorkflowAgentRunResult(77L, "识别完成"));
        when(assets.hasCoverage(7L, 9L, 101L, 77L)).thenReturn(false);

        assertThatThrownBy(() -> adapter.executeChild(plan(), task(), stage(), 101L, null))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("正式资产识别结果");
    }

    private WorkflowAgentExecutionPlan plan() {
        return new WorkflowAgentExecutionPlan(new WorkflowAgentRecord(
            1L, AssetRecognitionAgentBootstrap.AGENT_CODE, "识别", null, "prompt", 11L,
            new BigDecimal("0.2"), 1000, 4, "ENABLED", 1L, 1L, 1L, null, null,
            List.of("short-drama-analysis-foundation", "short-drama-asset-recognition-framework"),
            List.of("read_current_episode", "save_episode_assets")), List.of());
    }

    private ScriptAnalysisTaskEntity task() {
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(41L); task.setTenantId(7L); task.setProjectId(8L);
        task.setScriptId(9L); task.setCreatedBy(10L);
        return task;
    }

    private ScriptAnalysisStageEntity stage() {
        ScriptAnalysisStageEntity stage = new ScriptAnalysisStageEntity();
        stage.setId(42L); stage.setStageCode("CHARACTER_SCENE_RECOGNITION");
        return stage;
    }
}
