package com.antshorttv.ai;

public record AiTextRequest(
    String systemPrompt,
    String userPrompt,
    Double temperature,
    Integer maxTokens,
    Double topP,
    Boolean jsonMode,
    Object responseSchema,
    Integer timeoutSeconds,
    Integer retryCount
) {
    public AiTextRequest(String systemPrompt, String userPrompt, Double temperature, Integer maxTokens,
        Double topP, Boolean jsonMode, Object responseSchema) {
        this(systemPrompt, userPrompt, temperature, maxTokens, topP, jsonMode, responseSchema, null, null);
    }

    public AiTextRequest(
        String systemPrompt,
        String userPrompt,
        Double temperature,
        Integer maxTokens,
        Object responseSchema
    ) {
        this(systemPrompt, userPrompt, temperature, maxTokens, null, false, responseSchema, null, null);
    }
}
