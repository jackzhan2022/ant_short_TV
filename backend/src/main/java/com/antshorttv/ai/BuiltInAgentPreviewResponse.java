package com.antshorttv.ai;

public record BuiltInAgentPreviewResponse(
    String agentCode,
    String prompt,
    String outputSchema
) {
}
