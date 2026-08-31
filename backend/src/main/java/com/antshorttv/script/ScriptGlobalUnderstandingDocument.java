package com.antshorttv.script;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record ScriptGlobalUnderstandingDocument(
    Long id,
    Long tenantId,
    Long projectId,
    Long scriptId,
    Integer schemaVersion,
    JsonNode content,
    String analyzedContentHash,
    Long lastAgentRunId,
    Long createdBy,
    Long updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
