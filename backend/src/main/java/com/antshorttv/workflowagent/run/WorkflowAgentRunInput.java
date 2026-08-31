package com.antshorttv.workflowagent.run;

public record WorkflowAgentRunInput(
    String agentCode,
    String input,
    Long tenantId,
    Long projectId,
    Long episodeId,
    Long scriptId,
    Long taskId,
    Long analysisStageId,
    Long userId,
    Long executionId,
    Long attemptId,
    Integer executionVersion,
    Long modelIdOverride
) {
    public WorkflowAgentRunInput(
        String agentCode, String input, Long tenantId, Long projectId, Long episodeId,
        Long scriptId, Long taskId, Long analysisStageId, Long userId
    ) {
        this(agentCode, input, tenantId, projectId, episodeId, scriptId, taskId,
            analysisStageId, userId, null, null, null, null);
    }

    public WorkflowAgentRunInput(
        String agentCode,
        String input,
        Long tenantId,
        Long projectId,
        Long episodeId,
        Long taskId,
        Long userId
    ) {
        this(agentCode, input, tenantId, projectId, episodeId, null, taskId, null, userId,
            null, null, null, null);
    }
}
