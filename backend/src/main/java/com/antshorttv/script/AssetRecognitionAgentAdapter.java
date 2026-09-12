package com.antshorttv.script;

import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.workflowagent.agent.AssetRecognitionAgentBootstrap;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AssetRecognitionAgentAdapter {
    private final WorkflowAgentRunner runner;
    private final EpisodeAssetPersistenceService assets;
    private final EpisodePromptContextService contexts;

    @Autowired
    public AssetRecognitionAgentAdapter(
        WorkflowAgentRunner runner,
        EpisodeAssetPersistenceService assets,
        EpisodePromptContextService contexts
    ) {
        this.runner = runner;
        this.assets = assets;
        this.contexts = contexts;
    }

    AssetRecognitionAgentAdapter(
        WorkflowAgentRunner runner,
        EpisodeAssetPersistenceService assets
    ) {
        this(runner, assets, null);
    }

    public Execution executeChild(
        WorkflowAgentExecutionPlan plan,
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        Long episodeId,
        AiExecutionContext executionContext,
        Long modelId
    ) {
        return executeChild(plan, task, stage, episodeId, executionContext, modelId,
            AssetRecognitionScope.ALL, AssetPromptPolicy.FILL_EMPTY);
    }

    public Execution executeChild(
        WorkflowAgentExecutionPlan plan,
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        Long episodeId,
        AiExecutionContext executionContext,
        Long modelId,
        AssetRecognitionScope scope,
        AssetPromptPolicy promptPolicy
    ) {
        return execute(plan, task, stage, episodeId, executionContext, modelId, scope, promptPolicy, Map.of());
    }

    public Execution executeClaimedChild(
        WorkflowAgentExecutionPlan plan, ScriptAnalysisTaskEntity task, ScriptAnalysisStageEntity stage,
        EpisodeFanoutCoordinator.EpisodeUnit episode, AiExecutionContext executionContext, Long modelId
    ) {
        return execute(plan, task, stage, episode.episodeId(), executionContext, modelId,
            AssetRecognitionScope.ALL, AssetPromptPolicy.FILL_EMPTY, episode.trustedToolState());
    }

    private Execution execute(
        WorkflowAgentExecutionPlan plan, ScriptAnalysisTaskEntity task, ScriptAnalysisStageEntity stage,
        Long episodeId, AiExecutionContext executionContext, Long modelId,
        AssetRecognitionScope scope, AssetPromptPolicy promptPolicy, Map<String, Object> trustedToolState
    ) {
        boolean reextraction = executionContext != null
            && "SCRIPT_AI_OPERATION".equals(executionContext.task().businessType);
        EpisodePromptContextService.Prepared prepared = contexts == null ? null
            : reextraction ? contexts.prepareReextraction(task, episodeId, modelId)
            : contexts.prepare(task, episodeId, modelId);
        WorkflowAgentRunInput input = new WorkflowAgentRunInput(
            AssetRecognitionAgentBootstrap.AGENT_CODE,
            "基于服务端已准备的当前剧集正文和资产目录，识别、匹配并保存本集正式资产。"
                + " 本次仅处理范围：" + (scope == null ? AssetRecognitionScope.ALL : scope).name()
                + "；提示词策略：" + (promptPolicy == null ? AssetPromptPolicy.FILL_EMPTY : promptPolicy).name()
                + "。范围外数组必须为空。FILL_EMPTY仅补齐空提示词；REGENERATE_ALL可覆盖所选范围内提示词，"
                + "但不得删除人工资产或覆盖其名称、外观等人工编辑字段。",
            task.getTenantId(), task.getProjectId(), episodeId, task.getScriptId(), task.getId(),
            stage.getId(), task.getCreatedBy(),
            executionContext == null ? null : executionContext.task().id,
            executionContext == null ? null : executionContext.claim().attemptId(),
            executionContext == null ? null : executionContext.task().executionVersion,
            modelId, null,
            prepared == null ? null : prepared.commonPrefix(),
            prepared == null ? null : prepared.cacheKey(), Map.of(
                "assetScope", (scope == null ? AssetRecognitionScope.ALL : scope).name(),
                "assetPromptPolicy", (promptPolicy == null ? AssetPromptPolicy.FILL_EMPTY : promptPolicy).name()))
            .withTrustedToolState(trustedToolState);
        WorkflowAgentRunResult run = runner.runFormal(plan, input);
        if (!assets.hasCoverage(task.getTenantId(), task.getScriptId(), episodeId, run.runId())) {
            throw new IllegalStateException("Agent 未提交本次剧集正式资产识别结果。");
        }
        return new Execution(run.runId(), run.modelCalls());
    }

    public record Execution(Long agentRunId, List<WorkflowAgentModelCall> modelCalls) {
        public Execution { modelCalls = List.copyOf(modelCalls); }
    }
}
