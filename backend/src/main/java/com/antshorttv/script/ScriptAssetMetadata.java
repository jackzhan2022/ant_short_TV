package com.antshorttv.script;

import com.fasterxml.jackson.databind.JsonNode;

public record ScriptAssetMetadata(
    Long scriptId,
    String normalizedName,
    JsonNode content,
    String source,
    Long generatedByRunId
) {
}
