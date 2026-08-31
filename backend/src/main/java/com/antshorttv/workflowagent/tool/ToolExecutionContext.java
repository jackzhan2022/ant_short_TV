package com.antshorttv.workflowagent.tool;

import java.util.Set;
import java.time.Instant;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;

public record ToolExecutionContext(
    Long tenantId,
    Long userId,
    Long projectId,
    Long episodeId,
    Long scriptId,
    Long taskId,
    Long analysisStageId,
    Long agentRunId,
    Long executionId,
    Long attemptId,
    Integer executionVersion,
    Set<String> permissions,
    Instant deadline,
    WorkflowToolRunState runState,
    ReviewToolScope reviewScope
) {
    public ToolExecutionContext {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        runState = runState == null ? new WorkflowToolRunState() : runState;
    }

    public ToolExecutionContext(
        Long tenantId, Long userId, Long projectId, Long episodeId, Long scriptId,
        Long taskId, Long analysisStageId, Long agentRunId, Long executionId, Long attemptId,
        Integer executionVersion, Set<String> permissions, Instant deadline, WorkflowToolRunState runState
    ) {
        this(tenantId, userId, projectId, episodeId, scriptId, taskId, analysisStageId, agentRunId,
            executionId, attemptId, executionVersion, permissions, deadline, runState, null);
    }

    public ToolExecutionContext(
        Long tenantId, Long userId, Long projectId, Long episodeId, Long scriptId,
        Long taskId, Long analysisStageId, Long agentRunId, Set<String> permissions,
        Instant deadline, WorkflowToolRunState runState
    ) {
        this(tenantId, userId, projectId, episodeId, scriptId, taskId, analysisStageId,
            agentRunId, null, null, null, permissions, deadline, runState, null);
    }

    public ToolExecutionContext(
        Long tenantId, Long userId, Long projectId, Long episodeId, Long taskId,
        Set<String> permissions
    ) {
        this(tenantId, userId, projectId, episodeId, null, taskId, null, null, null, null, null,
            permissions, null, new WorkflowToolRunState(), null);
    }

    public ToolExecutionContext(
        Long tenantId, Long userId, Long projectId, Long episodeId, Long taskId,
        Set<String> permissions, Instant deadline
    ) {
        this(tenantId, userId, projectId, episodeId, null, taskId, null, null, null, null, null,
            permissions, deadline, new WorkflowToolRunState(), null);
    }

    public void requireBeforeDeadline() {
        if (deadline != null && !Instant.now().isBefore(deadline)) {
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_TIMEOUT, "Agent 工具执行超时。");
        }
    }
}
