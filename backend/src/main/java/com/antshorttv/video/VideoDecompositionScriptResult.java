package com.antshorttv.video;

import java.time.LocalDateTime;

public record VideoDecompositionScriptResult(
    Long id,
    Long tenantId,
    Long batchId,
    Long episodeId,
    Long analysisId,
    Long aiCallLogId,
    String content,
    String formatVersion,
    LocalDateTime createdAt
) {
}

record VideoDecompositionScriptResultCreate(
    Long tenantId,
    Long batchId,
    Long episodeId,
    Long analysisId,
    Long aiCallLogId,
    String content,
    String formatVersion
) {
}
