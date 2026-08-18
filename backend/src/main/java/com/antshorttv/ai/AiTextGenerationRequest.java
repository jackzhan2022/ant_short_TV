package com.antshorttv.ai;

public record AiTextGenerationRequest(
    Long tenantId,
    Long userId,
    String businessScene,
    String requestSummary,
    String systemPrompt,
    String userPrompt
) {
}
