package com.antshorttv.ai;

import java.util.List;

public record AiTextRequest(
    String systemPrompt,
    String userPrompt,
    Double temperature,
    Integer maxTokens,
    Double topP,
    Boolean jsonMode,
    Object responseSchema,
    Integer timeoutSeconds,
    Integer retryCount,
    List<AiChatMessage> messages,
    List<AiToolDefinition> tools
) {
    public AiTextRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public AiTextRequest(String systemPrompt, String userPrompt, Double temperature, Integer maxTokens,
        Double topP, Boolean jsonMode, Object responseSchema, Integer timeoutSeconds, Integer retryCount) {
        this(systemPrompt, userPrompt, temperature, maxTokens, topP, jsonMode, responseSchema,
            timeoutSeconds, retryCount, List.of(), List.of());
    }

    public AiTextRequest(String systemPrompt, String userPrompt, Double temperature, Integer maxTokens,
        Double topP, Boolean jsonMode, Object responseSchema) {
        this(systemPrompt, userPrompt, temperature, maxTokens, topP, jsonMode, responseSchema,
            null, null, List.of(), List.of());
    }

    public AiTextRequest(
        String systemPrompt,
        String userPrompt,
        Double temperature,
        Integer maxTokens,
        Object responseSchema
    ) {
        this(systemPrompt, userPrompt, temperature, maxTokens, null, false, responseSchema,
            null, null, List.of(), List.of());
    }
}
