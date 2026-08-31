package com.antshorttv.review;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.workflowagent.agent.ScriptReviewAgentBootstrap;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.antshorttv.workflowagent.tool.ReviewToolScope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReviewQuickAgentAdapter {
    private final ReviewAgentExecutionPlanFactory plans;
    private final WorkflowAgentRunner runner;
    private final ReviewTaskMapper tasks;
    private final ObjectMapper json;
    private final boolean enabled;

    public ReviewQuickAgentAdapter(ReviewAgentExecutionPlanFactory plans, WorkflowAgentRunner runner,
        ReviewTaskMapper tasks, ObjectMapper json,
        @Value("${ai.workflow-agent.review-quick-enabled:false}") boolean enabled) {
        this.plans = plans;
        this.runner = runner;
        this.tasks = tasks;
        this.json = json;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public Execution execute(ReviewTaskEntity task, AiExecutionContext execution, Long modelId) {
        if (execution == null) throw invalid("AI 调用必须先创建执行和积分预占。");
        List<String> dimensions = dimensions(task.getSelectedDimensionsJson());
        WorkflowAgentExecutionPlan plan = plans.freeze(dimensions, "QUICK");
        int attempt = task.getWorkflowAttemptNo() == null ? 1 : task.getWorkflowAttemptNo() + 1;
        task.setWorkflowAgentCode(ScriptReviewAgentBootstrap.AGENT_CODE);
        task.setWorkflowAgentRevision(plan.agent().revision());
        task.setWorkflowPhase("QUICK");
        task.setWorkflowAttemptNo(attempt);
        task.setCurrentStage("AGENT_QUICK");
        task.setCurrentAction("Agent 正在读取可信剧本范围并执行快速审核");
        task.setUpdatedAt(LocalDateTime.now());
        tasks.updateById(task);
        ReviewToolScope scope = new ReviewToolScope(task.getProjectId(), task.getScriptVersionId(),
            null, null, attempt, "QUICK", dimensions);
        WorkflowAgentRunResult run = runner.runFormal(plan, new WorkflowAgentRunInput(
            ScriptReviewAgentBootstrap.AGENT_CODE,
            "审核当前冻结剧本范围。按顺序读取上下文、正文和历史，最后仅调用一次正式保存工具。",
            task.getTenantId(), task.getProjectId(), null, null, task.getId(), null, task.getCreatedBy(),
            execution.task().id, execution.claim().attemptId(), execution.task().executionVersion, modelId, scope));
        ReviewTaskEntity committed = tasks.selectById(task.getId());
        if (committed == null || !"COMPLETED".equals(committed.getStatus())
            || !run.runId().equals(committed.getWorkflowAgentRunId())) {
            throw invalid("审核 Agent 未提交本次正式结果。");
        }
        return new Execution(run.runId(), run.modelCalls());
    }

    private List<String> dimensions(String value) {
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw invalid("审核维度配置无效。"); }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    public record Execution(Long runId, List<WorkflowAgentModelCall> modelCalls) {}
}
