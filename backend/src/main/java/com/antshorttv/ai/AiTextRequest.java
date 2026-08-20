package com.antshorttv.ai;

public record AiTextRequest(
    String systemPrompt,
    String userPrompt,
    Double temperature,
    Integer maxTokens,
    Object responseSchema
) {
}
