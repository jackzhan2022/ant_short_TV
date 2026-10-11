package com.antshorttv.video;

import java.util.Map;

public record VideoUnderstandingResponse(
    String content,
    String providerRequestId,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    Long durationMs,
    Map<String, Object> metadata,
    Integer cachedInputTokens,
    Integer cacheWriteTokens
) {
    public VideoUnderstandingResponse(String content, String providerRequestId, Integer promptTokens,
        Integer completionTokens, Integer totalTokens, Long durationMs, Map<String, Object> metadata) {
        this(content, providerRequestId, promptTokens, completionTokens, totalTokens, durationMs, metadata, null, null);
    }
}
