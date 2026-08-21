package com.antshorttv.video;

import com.fasterxml.jackson.databind.JsonNode;

public record VideoAnalysis(
    JsonNode characters,
    JsonNode scenes,
    JsonNode props,
    JsonNode timeline,
    JsonNode dialogue,
    JsonNode actions,
    JsonNode emotions,
    String normalizedJson
) {
}
