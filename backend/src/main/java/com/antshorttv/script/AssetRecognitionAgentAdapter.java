package com.antshorttv.script;

import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.workflowagent.agent.AssetRecognitionAgentBootstrap;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AssetRecognitionAgentAdapter {
    private final WorkflowAgentRunner runner;
    private final EpisodeAssetPersistenceService assets;
    private final boolean enabled;

    public AssetRecognitionAgentAdapter(
        WorkflowAgentRunner runner,
        EpisodeAssetPersistenceService assets,
        @Value("${ai.workflow-agent.asset-recognition-enabled:false}") boolean enabled
    ) {
        this.runner = runner;
        this.assets = assets;
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }

    public Execution executeChild(
        WorkflowAgentExecutionPlan plan,
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        Long episodeId,
        AiExecutionContext executionContext,
        Long modelId
    ) {
        WorkflowAgentRunInput input = new WorkflowAgentRunInput(
            AssetRecognitionAgentBootstrap.AGENT_CODE,
            "读取当前剧集并识别、匹配、保存本集正式角色、变装、场景、道具及形态。",
            task.getTenantId(), task.getProjectId(), episodeId, task.getScriptId(), task.getId(),
            stage.getId(), task.getCreatedBy(),
            executionContext == null ? null : executionContext.task().id,
            executionContext == null ? null : executionContext.claim().attemptId(),
            executionContext == null ? null : executionContext.task().executionVersion,
            modelId);
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
