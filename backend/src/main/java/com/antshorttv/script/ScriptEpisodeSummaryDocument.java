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
}
