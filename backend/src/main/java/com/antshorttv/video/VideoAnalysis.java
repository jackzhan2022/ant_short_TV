package com.antshorttv.video;

import com.fasterxml.jackson.databind.JsonNode;

public record VideoAnalysis(
    String script,
    String normalizedJson
) {
}
