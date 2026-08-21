package com.antshorttv.video;

import java.util.Map;

public record VideoUnderstandingResponse(
    String content,
    String providerRequestId,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    Long durationMs,
    Map<String, Object> metadata
) {
}
