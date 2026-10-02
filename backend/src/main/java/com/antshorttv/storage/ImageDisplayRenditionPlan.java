package com.antshorttv.storage;

public record ImageDisplayRenditionPlan(
    String objectKey,
    String mimeType,
    String processRule
) {
}
