package com.antshorttv.ai;

import java.util.List;
import java.util.Map;

public record AiImageResponse(
    List<String> imageUrls,
    String providerRequestId,
    Long durationMs,
    Map<String, Object> metadata
) {
}
