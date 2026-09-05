package com.antshorttv.ai;

import java.util.List;
import java.util.Map;

public record AiTextResponse(
    String content,
    String providerRequestId,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    Long durationMs,
    Map<String, Object> metadata,
    String finishReason,
    boolean truncated,
    List<AiToolCall> toolCalls,
    Integer cachedInputTokens,
    Integer cacheWriteTokens
) {
    public AiTextResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public AiTextResponse(String content, String providerRequestId, Integer promptTokens,
        Integer completionTokens, Integer totalTokens, Long durationMs, Map<String, Object> metadata,
        String finishReason, boolean truncated, List<AiToolCall> toolCalls) {
        this(content, providerRequestId, promptTokens, completionTokens, totalTokens, durationMs,
            metadata, finishReason, truncated, toolCalls, null, null);
    }

    public AiTextResponse(String content, String providerRequestId, Integer promptTokens,
        Integer completionTokens, Integer totalTokens, long durationMs, Map<String, Object> metadata,
        String finishReason, boolean truncated) {
        this(content, providerRequestId, promptTokens, completionTokens, totalTokens, durationMs,
            metadata, finishReason, truncated, List.of(), null, null);
    }

    public AiTextResponse(String content, String providerRequestId, Integer promptTokens,
        Integer completionTokens, Integer totalTokens, long durationMs, Map<String, Object> metadata) {
        this(content, providerRequestId, promptTokens, completionTokens, totalTokens, durationMs,
            metadata, null, false, List.of(), null, null);
    }
}
