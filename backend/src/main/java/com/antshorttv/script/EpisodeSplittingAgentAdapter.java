package com.antshorttv.script;

import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.workflowagent.agent.EpisodeSplittingAgentBootstrap;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EpisodeSplittingAgentAdapter {
    private final WorkflowAgentRunner runner;
    private final ScriptEpisodeService episodes;
    private final boolean enabled;

    public EpisodeSplittingAgentAdapter(
        WorkflowAgentRunner runner,
        ScriptEpisodeService episodes,
        @Value("${ai.workflow-agent.episode-splitting-enabled:false}") boolean enabled
    ) {
        this.runner = runner;
        this.episodes = episodes;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public Execution execute(
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        AiExecutionContext executionContext,
        Long modelId
    ) {
        WorkflowAgentRunResult run = runner.runFormal(new WorkflowAgentRunInput(
            EpisodeSplittingAgentBootstrap.AGENT_CODE,
            "读取当前剧本，调用 AI 识别全部分集边界，并保存正式剧集集合。",
            task.getTenantId(), task.getProjectId(), null, task.getScriptId(), task.getId(),
            stage.getId(), task.getCreatedBy(),
            executionContext == null ? null : executionContext.task().id,
            executionContext == null ? null : executionContext.claim().attemptId(),
            executionContext == null ? null : executionContext.task().executionVersion,
            modelId));
        List<ScriptEpisodeResponse> formal = episodes.currentEpisodes(
            task.getTenantId(), task.getProjectId(), task.getScriptId());
        if (formal.isEmpty() || formal.stream().anyMatch(item -> !run.runId().equals(item.generatedByRunId()))) {
            throw new IllegalStateException("Agent 未提交本次完整正式剧集集合。");
        }
        return new Execution(formal, run.runId(), run.modelCalls());
    }

    public record Execution(
        List<ScriptEpisodeResponse> episodes,
        Long agentRunId,
        List<WorkflowAgentModelCall> modelCalls
    ) {
        public Execution {
            episodes = List.copyOf(episodes);
            modelCalls = List.copyOf(modelCalls);
        }
    }
}
