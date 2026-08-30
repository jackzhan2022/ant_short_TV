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
    Long taskId,
    Set<String> permissions,
    Instant deadline
) {
    public ToolExecutionContext {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public ToolExecutionContext(
        Long tenantId, Long userId, Long projectId, Long episodeId, Long taskId,
        Set<String> permissions
    ) {
        this(tenantId, userId, projectId, episodeId, taskId, permissions, null);
    }

    public void requireBeforeDeadline() {
        if (deadline != null && !Instant.now().isBefore(deadline)) {
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_TIMEOUT, "Agent 工具执行超时。");
        }
    }
}
