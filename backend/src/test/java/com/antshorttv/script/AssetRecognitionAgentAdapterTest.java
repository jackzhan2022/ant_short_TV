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
    void reextractionUsesItsOwnFrozenStyleInsteadOfOriginalAnalysisStyle() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var styles = new com.antshorttv.style.AssetArtStyleService(mock(com.antshorttv.project.ProjectMapper.class), json);
        var operations = mock(ScriptAiOperationMapper.class);
        var operation = new ScriptAiOperationEntity();
        operation.tenantId = 7L; operation.projectId = 8L;
        operation.artStyleSnapshot = json.writeValueAsString(new com.antshorttv.style.AssetArtStyleService.Snapshot(
            "V2", "frozen-operation", styles.catalog().stream().filter(s -> s.code().equals("3D_clay_stopmotion")).findFirst().orElseThrow()));
        when(operations.selectById(500L)).thenReturn(operation);
        var adapter = new AssetRecognitionAgentAdapter(runner, assets);
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "artStyles", styles);
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "operations", operations);
        var execution = new com.antshorttv.execution.AiExecutionTaskEntity();
        execution.businessType = "SCRIPT_AI_OPERATION"; execution.businessId = 500L;
        when(runner.runFormal(any(WorkflowAgentExecutionPlan.class), any(WorkflowAgentRunInput.class)))
            .thenReturn(new WorkflowAgentRunResult(77L, "{}"));
        when(assets.hasCoverage(7L, 9L, 101L, 77L)).thenReturn(true);
        adapter.executeChild(plan(), task(), stage(), 101L,
            new com.antshorttv.execution.AiExecutionContext(execution,
                new com.antshorttv.execution.AiExecutionClaim(1L, 2L, "claim", 1, "SUBMIT")), 99L);
        var input = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner).runFormal(any(WorkflowAgentExecutionPlan.class), input.capture());
        assertThat(input.getValue().input()).contains("frozen-operation", "黏土");
    }

    @Test
    void loadsFrozenAssetStyleWithoutChangingSharedEpisodeCache() {
        var styles = new com.antshorttv.style.AssetArtStyleService(
            mock(com.antshorttv.project.ProjectMapper.class), new com.fasterxml.jackson.databind.ObjectMapper());
        var contexts = mock(EpisodePromptContextService.class);
        when(contexts.prepare(any(), any(), any())).thenReturn(
            new EpisodePromptContextService.Prepared(501L, "COMMON_PREFIX", "CACHE_KEY", "HASH"));
        var adapter = new AssetRecognitionAgentAdapter(runner, assets, contexts);
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "artStyles", styles);
        var task = task();
        task.setArtStyleSnapshot(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(
            new com.antshorttv.style.AssetArtStyleService.Snapshot("V2", "frozen-r1",
                styles.catalog().stream().filter(s -> s.code().equals("realpeople_modern_city")).findFirst().orElseThrow())).toString());
        when(runner.runFormal(any(WorkflowAgentExecutionPlan.class), any(WorkflowAgentRunInput.class)))
            .thenReturn(new WorkflowAgentRunResult(77L, "{}"));
        when(assets.hasCoverage(7L, 9L, 101L, 77L)).thenReturn(true);
        adapter.executeChild(plan(), task, stage(), 101L, null, 99L, AssetRecognitionScope.PROP, AssetPromptPolicy.FILL_EMPTY);
        var input = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner).runFormal(any(WorkflowAgentExecutionPlan.class), input.capture());
        assertThat(input.getValue().input()).contains("frozen-r1", "道具美术").doesNotContain("角色美术", "场景美术");
        assertThat(input.getValue().stableContext()).isEqualTo("COMMON_PREFIX");
        assertThat(input.getValue().promptCacheKey()).isEqualTo("CACHE_KEY");
    }

    @Test
    void reusesTheSameFrozenContextContractWithoutSummaryData() {
        EpisodePromptContextService contexts = mock(EpisodePromptContextService.class);
        when(contexts.prepare(any(), any(), any())).thenReturn(
            new EpisodePromptContextService.Prepared(501L, "COMMON_PREFIX", "CACHE_KEY", "HASH"));
        AssetRecognitionAgentAdapter adapter = new AssetRecognitionAgentAdapter(runner, assets, contexts);
        when(runner.runFormal(any(WorkflowAgentExecutionPlan.class), any(WorkflowAgentRunInput.class)))
            .thenReturn(new WorkflowAgentRunResult(77L, "{\"saved\":true}"));
        when(assets.hasCoverage(7L, 9L, 101L, 77L)).thenReturn(true);

        adapter.executeClaimedChild(plan(), task(), stage(),
            new EpisodeFanoutCoordinator.EpisodeUnit(101L, "e1", "fp", "RUNNING", 50L, 2), null, 99L);

        ArgumentCaptor<WorkflowAgentRunInput> input = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner).runFormal(any(WorkflowAgentExecutionPlan.class), input.capture());
        assertThat(input.getValue().stableContext()).isEqualTo("COMMON_PREFIX");
        assertThat(input.getValue().promptCacheKey()).isEqualTo("CACHE_KEY");
        assertThat(input.getValue().trustedToolState()).containsEntry("fanoutSnapshotId", 50L)
            .containsEntry("fanoutUnitAttemptNo", 2)
            .containsEntry("assetScope", "ALL")
            .containsEntry("assetPromptPolicy", "FILL_EMPTY");
        assertThat(input.getValue().promptCacheOptions()).isEmpty();
        assertThat(input.getValue().input()).doesNotContain("概要");
    }

    @Test
    void runsOneEpisodeAgainstFrozenPlanAndRequiresFormalCoverage() {
        AssetRecognitionAgentAdapter adapter = new AssetRecognitionAgentAdapter(runner, assets);
        WorkflowAgentExecutionPlan plan = plan();
        when(runner.runFormal(any(WorkflowAgentExecutionPlan.class), any(WorkflowAgentRunInput.class)))
            .thenReturn(new WorkflowAgentRunResult(77L, "{\"saved\":true}"));
        when(assets.hasCoverage(7L, 9L, 101L, 77L)).thenReturn(true);

        AssetRecognitionAgentAdapter.Execution result = adapter.executeChild(
            plan, task(), stage(), 101L, null, 99L);

        ArgumentCaptor<WorkflowAgentRunInput> input = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner).runFormal(any(WorkflowAgentExecutionPlan.class), input.capture());
        assertThat(input.getValue().episodeId()).isEqualTo(101L);
        assertThat(input.getValue().modelIdOverride()).isEqualTo(99L);
        assertThat(input.getValue().input()).doesNotContain("概要", "全局理解");
        assertThat(result.agentRunId()).isEqualTo(77L);
    }

    @Test
    void forwardsCharacterScopeToTheFormalAgentRun() {
        AssetRecognitionAgentAdapter adapter = new AssetRecognitionAgentAdapter(runner, assets);
        when(runner.runFormal(any(WorkflowAgentExecutionPlan.class), any(WorkflowAgentRunInput.class)))
            .thenReturn(new WorkflowAgentRunResult(77L, "{\"saved\":true}"));
        when(assets.hasCoverage(7L, 9L, 101L, 77L)).thenReturn(true);

        adapter.executeChild(plan(), task(), stage(), 101L, null, 99L, AssetRecognitionScope.CHARACTER,
            AssetPromptPolicy.FILL_EMPTY);

        ArgumentCaptor<WorkflowAgentRunInput> input = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner).runFormal(any(WorkflowAgentExecutionPlan.class), input.capture());
        assertThat(input.getValue().trustedToolState())
            .containsEntry("assetScope", "CHARACTER")
            .containsEntry("assetPromptPolicy", "FILL_EMPTY");
        assertThat(input.getValue().promptCacheOptions()).isEmpty();
        assertThat(input.getValue().input()).contains("FILL_EMPTY仅补齐空提示词", "REGENERATE_ALL可覆盖", "不得删除人工资产");
    }

    @Test
    void keepsRegenerationPolicyInToolStateWithoutProviderCacheOptions() {
        AssetRecognitionAgentAdapter adapter = new AssetRecognitionAgentAdapter(runner, assets);
        when(runner.runFormal(any(WorkflowAgentExecutionPlan.class), any(WorkflowAgentRunInput.class)))
            .thenReturn(new WorkflowAgentRunResult(77L, "{\"saved\":true}"));
        when(assets.hasCoverage(7L, 9L, 101L, 77L)).thenReturn(true);

        adapter.executeChild(plan(), task(), stage(), 101L, null, 99L, AssetRecognitionScope.PROP,
            AssetPromptPolicy.REGENERATE_ALL);

        ArgumentCaptor<WorkflowAgentRunInput> input = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner).runFormal(any(WorkflowAgentExecutionPlan.class), input.capture());
        assertThat(input.getValue().trustedToolState())
            .containsEntry("assetScope", "PROP")
            .containsEntry("assetPromptPolicy", "REGENERATE_ALL");
        assertThat(input.getValue().promptCacheOptions()).isEmpty();
    }

    @Test
    void rejectsModelTextWithoutTerminalFormalSave() {
        AssetRecognitionAgentAdapter adapter = new AssetRecognitionAgentAdapter(runner, assets);
        when(runner.runFormal(any(WorkflowAgentExecutionPlan.class), any(WorkflowAgentRunInput.class)))
            .thenReturn(new WorkflowAgentRunResult(77L, "识别完成"));
        when(assets.hasCoverage(7L, 9L, 101L, 77L)).thenReturn(false);

        assertThatThrownBy(() -> adapter.executeChild(plan(), task(), stage(), 101L, null, 99L))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("正式资产识别结果");
    }

    private WorkflowAgentExecutionPlan plan() {
        return new WorkflowAgentExecutionPlan(new WorkflowAgentRecord(
            1L, AssetRecognitionAgentBootstrap.AGENT_CODE, "识别", null, "prompt", 11L,
            new BigDecimal("0.2"), 1000, 4, "ENABLED", 1L, 1L, 1L, null, null,
            List.of("short-drama-analysis-foundation", "short-drama-asset-recognition-framework"),
            List.of("read_current_episode", "search_script_assets", "read_asset_details",
                "save_episode_assets")), List.of());
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
