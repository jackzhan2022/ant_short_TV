package com.antshorttv.script;

import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.workflowagent.agent.EpisodeSummaryAgentBootstrap;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EpisodeSummaryAgentAdapter {
    private final WorkflowAgentRunner runner;
    private final ScriptEpisodeSummaryRepository summaries;
    private final boolean enabled;

    public EpisodeSummaryAgentAdapter(
        WorkflowAgentRunner runner,
        ScriptEpisodeSummaryRepository summaries,
        @Value("${ai.workflow-agent.episode-summary-enabled:false}") boolean enabled
    ) {
        this.runner = runner;
        this.summaries = summaries;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public Execution executeChild(
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        Long episodeId,
        AiExecutionContext executionContext,
        Long modelId
    ) {
        return executeChild(null, task, stage, episodeId, executionContext, modelId);
    }

    public Execution executeChild(
        WorkflowAgentExecutionPlan plan,
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        Long episodeId,
        AiExecutionContext executionContext,
        Long modelId
    ) {
        WorkflowAgentRunInput input = new WorkflowAgentRunInput(
            EpisodeSummaryAgentBootstrap.AGENT_CODE,
            "读取当前剧集并提炼、保存本集正式概要。",
            task.getTenantId(), task.getProjectId(), episodeId, task.getScriptId(), task.getId(),
            stage.getId(), task.getCreatedBy(),
            executionContext == null ? null : executionContext.task().id,
            executionContext == null ? null : executionContext.claim().attemptId(),
            executionContext == null ? null : executionContext.task().executionVersion,
            modelId);
        WorkflowAgentRunResult run = plan == null ? runner.runFormal(input) : runner.runFormal(plan, input);
        ScriptEpisodeSummaryDocument document = summaries.findCurrent(
                task.getTenantId(), task.getScriptId(), episodeId)
            .filter(item -> run.runId().equals(item.generatedByRunId()))
            .orElseThrow(() -> new IllegalStateException("Agent 未提交本次剧集正式概要。"));
        return new Execution(document, run.runId(), run.modelCalls());
    }

    public record Execution(
        ScriptEpisodeSummaryDocument summary,
        Long agentRunId,
        List<WorkflowAgentModelCall> modelCalls
    ) {
        public Execution {
            modelCalls = List.copyOf(modelCalls);
        }
    }
}
