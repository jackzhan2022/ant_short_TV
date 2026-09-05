package com.antshorttv.ai;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    List<AiToolDefinition> tools,
    String thinkingMode,
    String promptCacheKey,
    Map<String, Object> promptCacheOptions
) {
    public AiTextRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        promptCacheOptions = promptCacheOptions == null ? Map.of() : Map.copyOf(promptCacheOptions);
        if (thinkingMode != null && !Set.of("enabled", "disabled").contains(thinkingMode)) {
            throw new IllegalArgumentException("thinkingMode 必须为 enabled 或 disabled。");
        }
    }

    public AiTextRequest(String systemPrompt, String userPrompt, Double temperature, Integer maxTokens,
        Double topP, Boolean jsonMode, Object responseSchema, Integer timeoutSeconds, Integer retryCount,
        List<AiChatMessage> messages, List<AiToolDefinition> tools, String thinkingMode) {
        this(systemPrompt, userPrompt, temperature, maxTokens, topP, jsonMode, responseSchema,
            timeoutSeconds, retryCount, messages, tools, thinkingMode, null, Map.of());
    }

    public AiTextRequest(String systemPrompt, String userPrompt, Double temperature, Integer maxTokens,
        Double topP, Boolean jsonMode, Object responseSchema, Integer timeoutSeconds, Integer retryCount,
        List<AiChatMessage> messages, List<AiToolDefinition> tools) {
        this(systemPrompt, userPrompt, temperature, maxTokens, topP, jsonMode, responseSchema,
            timeoutSeconds, retryCount, messages, tools, null, null, Map.of());
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
