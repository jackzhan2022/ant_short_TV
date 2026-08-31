package com.antshorttv.script;

import com.antshorttv.workflowagent.agent.GlobalUnderstandingAgentBootstrap;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.antshorttv.workflowagent.run.WorkflowAgentRunRepository;
import com.antshorttv.execution.AiExecutionContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class GlobalUnderstandingAgentAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalUnderstandingAgentAdapter.class);
    private final WorkflowAgentRunner runner;
    private final ScriptGlobalUnderstandingRepository documents;
    private final WorkflowAgentRunRepository runs;
    private final boolean enabled;

    public GlobalUnderstandingAgentAdapter(
        WorkflowAgentRunner runner,
        ScriptGlobalUnderstandingRepository documents,
        WorkflowAgentRunRepository runs,
        @Value("${ai.workflow-agent.global-understanding-enabled:false}") boolean enabled
    ) {
        this.runner = runner;
        this.documents = documents;
        this.runs = runs;
        this.enabled = enabled;
        LOG.info("Global-understanding workflow Agent adapter enabled={}", enabled);
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
        WorkflowAgentRunResult run;
        try {
            run = runner.runFormal(new WorkflowAgentRunInput(
                GlobalUnderstandingAgentBootstrap.AGENT_CODE,
                "基于当前剧本生成并保存剧情全局理解正式数据。",
                task.getTenantId(), task.getProjectId(), null, task.getScriptId(), task.getId(),
                stage.getId(), task.getCreatedBy(),
                executionContext == null ? null : executionContext.task().id,
                executionContext == null ? null : executionContext.claim().attemptId(),
                executionContext == null ? null : executionContext.task().executionVersion,
                modelId));
        } catch (RuntimeException failure) {
            return recoverCommitted(task, stage).orElseThrow(() -> failure);
        }
        ScriptGlobalUnderstandingDocument document = documents
            .findCurrent(task.getTenantId(), task.getScriptId())
            .filter(current -> run.runId().equals(current.lastAgentRunId()))
            .orElseThrow(() -> new IllegalStateException("Agent 未提交本次剧情全局理解正式数据。"));
        return new Execution(document.content(), run.runId(), run.modelCalls());
    }

    private java.util.Optional<Execution> recoverCommitted(
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage
    ) {
        return documents.findCurrent(task.getTenantId(), task.getScriptId())
            .filter(document -> document.lastAgentRunId() != null)
            .filter(document -> runs.belongsToStage(
                document.lastAgentRunId(), task.getTenantId(), task.getId(), stage.getId()))
            .filter(document -> stageCommitted(task, stage))
            .map(document -> {
                String output = "{\"saved\":true,\"reconciled\":true}";
                runs.reconcileCommitted(document.lastAgentRunId(), output);
                return new Execution(document.content(), document.lastAgentRunId(),
                    runs.modelCalls(document.lastAgentRunId(), task.getTenantId()));
            });
    }

    private boolean stageCommitted(ScriptAnalysisTaskEntity task, ScriptAnalysisStageEntity stage) {
        return documents.stageSucceeded(task.getTenantId(), task.getId(), stage.getId());
    }

    public record Execution(
        JsonNode content,
        Long agentRunId,
        List<WorkflowAgentModelCall> modelCalls
    ) {
    }
}
