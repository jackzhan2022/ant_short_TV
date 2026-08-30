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
    Long taskId,
    Long modelId,
    BigDecimal temperature,
    Integer maxTokens,
    Integer maxSteps,
    String promptSnapshot,
    List<WorkflowAgentSkillSnapshot> skillSnapshots,
    List<String> toolCodes
) {
}
