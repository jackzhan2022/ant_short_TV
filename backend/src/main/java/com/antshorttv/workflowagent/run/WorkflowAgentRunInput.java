package com.antshorttv.workflowagent.run;

import java.util.Map;

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
    Long modelIdOverride,
    com.antshorttv.workflowagent.tool.ReviewToolScope reviewScope,
    String stableContext,
    String promptCacheKey,
    Map<String, Object> promptCacheOptions
) {
    public WorkflowAgentRunInput {
        promptCacheOptions = promptCacheOptions == null ? Map.of() : Map.copyOf(promptCacheOptions);
    }

    public WorkflowAgentRunInput(
        String agentCode, String input, Long tenantId, Long projectId, Long episodeId,
        Long scriptId, Long taskId, Long analysisStageId, Long userId, Long executionId,
        Long attemptId, Integer executionVersion, Long modelIdOverride,
        com.antshorttv.workflowagent.tool.ReviewToolScope reviewScope
    ) {
        this(agentCode, input, tenantId, projectId, episodeId, scriptId, taskId, analysisStageId,
            userId, executionId, attemptId, executionVersion, modelIdOverride, reviewScope,
            null, null, Map.of());
    }

    public WorkflowAgentRunInput(
        String agentCode, String input, Long tenantId, Long projectId, Long episodeId,
        Long scriptId, Long taskId, Long analysisStageId, Long userId, Long executionId,
        Long attemptId, Integer executionVersion, Long modelIdOverride
    ) {
        this(agentCode, input, tenantId, projectId, episodeId, scriptId, taskId, analysisStageId,
            userId, executionId, attemptId, executionVersion, modelIdOverride, null,
            null, null, Map.of());
    }

    public WorkflowAgentRunInput(
        String agentCode, String input, Long tenantId, Long projectId, Long episodeId,
        Long scriptId, Long taskId, Long analysisStageId, Long userId
    ) {
        this(agentCode, input, tenantId, projectId, episodeId, scriptId, taskId,
            analysisStageId, userId, null, null, null, null, null,
            null, null, Map.of());
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
            null, null, null, null, null, null, null, Map.of());
    }
}
