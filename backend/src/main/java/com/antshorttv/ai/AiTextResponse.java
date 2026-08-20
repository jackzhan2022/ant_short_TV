package com.antshorttv.ai;

import java.util.Map;

public record AiTextResponse(
    String content,
    String providerRequestId,
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    Long durationMs,
    Map<String, Object> metadata
) {
}
