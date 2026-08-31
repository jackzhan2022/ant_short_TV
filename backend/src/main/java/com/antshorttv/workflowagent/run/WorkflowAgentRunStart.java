package com.antshorttv.workflowagent.run;

import java.math.BigDecimal;
import java.util.List;

public record WorkflowAgentRunStart(
    Long agentId,
    String agentCode,
    String runType,
    Long tenantId,
    Long userId,
    Long projectId,
    Long episodeId,
    Long scriptId,
    Long taskId,
    Long analysisStageId,
    Long modelId,
    BigDecimal temperature,
    Integer maxTokens,
    Integer maxSteps,
    String promptSnapshot,
    List<WorkflowAgentSkillSnapshot> skillSnapshots,
    List<String> toolCodes
) {
    public WorkflowAgentRunStart(
        Long agentId, String agentCode, String runType, Long tenantId, Long userId,
        Long projectId, Long episodeId, Long taskId, Long modelId, BigDecimal temperature,
        Integer maxTokens, Integer maxSteps, String promptSnapshot,
        List<WorkflowAgentSkillSnapshot> skillSnapshots, List<String> toolCodes
    ) {
        this(agentId, agentCode, runType, tenantId, userId, projectId, episodeId, null,
            taskId, null, modelId, temperature, maxTokens, maxSteps, promptSnapshot,
            skillSnapshots, toolCodes);
    }
}
