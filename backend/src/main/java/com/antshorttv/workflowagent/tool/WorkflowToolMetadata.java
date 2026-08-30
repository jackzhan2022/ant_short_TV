package com.antshorttv.workflowagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkflowToolMetadata(
    String code,
    String name,
    String description,
    JsonNode inputSchema,
    JsonNode outputSchema,
    ToolRiskLevel riskLevel,
    ToolFailurePolicy failurePolicy
) {
}
