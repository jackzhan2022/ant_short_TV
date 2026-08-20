package com.antshorttv.ai;

import java.util.List;

public record AiImageRequest(
    String prompt,
    String negativePrompt,
    String size,
    String aspectRatio,
    Integer count,
    List<String> referenceImages
) {
}
