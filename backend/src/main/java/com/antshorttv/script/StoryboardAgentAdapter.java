package com.antshorttv.script;

import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionAttemptEntity;
import com.antshorttv.execution.AiExecutionAttemptMapper;
import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.workflowagent.agent.StoryboardAgentBootstrap;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.antshorttv.workflowagent.tool.StoryboardToolDataService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StoryboardAgentAdapter {
    private final WorkflowAgentRunner runner;
    private final StoryboardToolDataService storyboards;
    private final AiExecutionAttemptMapper attempts;
    private final boolean enabled;

    public StoryboardAgentAdapter(
        WorkflowAgentRunner runner,
        StoryboardToolDataService storyboards,
        AiExecutionAttemptMapper attempts,
        @Value("${ai.workflow-agent.storyboard-enabled:false}") boolean enabled
    ) {
        this.runner = runner;
        this.storyboards = storyboards;
        this.attempts = attempts;
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }

    public Execution execute(
        ScriptAiOperationEntity operation,
        Long episodeId,
        AiExecutionContext executionContext
    ) {
        if (!enabled) throw new IllegalStateException("分镜 Workflow Agent 尚未启用。");
        WorkflowAgentRunInput input = new WorkflowAgentRunInput(
            StoryboardAgentBootstrap.AGENT_CODE,
            "使用服务端准备的可信上下文，按 schemaVersion 3 规划当前集全部分镜，并一次调用 save_episode_storyboards 正式保存。",
            operation.tenantId, operation.projectId, episodeId, operation.scriptId, operation.id,
            null, operation.createdBy, executionContext.task().id, executionContext.claim().attemptId(),
            executionContext.task().executionVersion,
            executionContext.task().resolvedModelId == null
                ? executionContext.task().requestedModelId : executionContext.task().resolvedModelId);
        try {
            WorkflowAgentRunResult run = runner.runFormal(input);
            if (!storyboards.hasCompleteRunSet(
                operation.tenantId, operation.projectId, episodeId, run.runId())) {
                throw new IllegalStateException("Agent 未提交本次剧集完整正式分镜。");
            }
            AiExecutionAttemptEntity attempt = attempts.selectById(executionContext.claim().attemptId());
            int technicalRetryCount = attempt == null || attempt.retryCount == null ? 0 : attempt.retryCount;
            storyboards.annotateRunDiagnostics(operation.tenantId, operation.projectId, episodeId,
                run.runId(), run.modelCalls().size(), technicalRetryCount);
            return new Execution(run.runId(), run.modelCalls());
        } catch (AiGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new NonRetryableStoryboardException(exception.getMessage(), exception);
        }
    }

    public record Execution(Long agentRunId, List<WorkflowAgentModelCall> modelCalls) {
        public Execution { modelCalls = List.copyOf(modelCalls); }
    }
}
