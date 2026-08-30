package com.antshorttv.workflowagent.agent;

import java.math.BigDecimal;
import java.util.List;

public record WorkflowAgentCommand(
    String code,
    String name,
    String description,
    String systemPrompt,
    Long modelId,
    BigDecimal temperature,
    Integer maxTokens,
    Integer maxSteps,
    String status,
    List<String> skillCodes,
    List<String> toolCodes
) {
}
