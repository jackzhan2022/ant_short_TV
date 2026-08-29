package com.antshorttv.ai;

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
    boolean truncated
) {
    public AiTextResponse(String content, String providerRequestId, Integer promptTokens,
        Integer completionTokens, Integer totalTokens, long durationMs, Map<String, Object> metadata) {
        this(content, providerRequestId, promptTokens, completionTokens, totalTokens, durationMs, metadata, null, false);
    }
}
