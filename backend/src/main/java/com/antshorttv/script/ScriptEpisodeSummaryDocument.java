package com.antshorttv.script;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record ScriptEpisodeSummaryDocument(
    Long id,
    Long tenantId,
    Long projectId,
    Long scriptId,
    Long episodeId,
    Integer schemaVersion,
    JsonNode content,
    String source,
    Long generatedByRunId,
    Long createdBy,
    Long updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static String summaryText(ScriptEpisodeSummaryDocument document) {
        return document == null || document.content() == null
            ? null : document.content().path("summary").asText(null);
    }
}
