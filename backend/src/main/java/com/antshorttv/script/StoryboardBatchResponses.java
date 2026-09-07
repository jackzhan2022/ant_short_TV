package com.antshorttv.script;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

record StoryboardBatchItemResponse(
    Long id,
    Long episodeId,
    Integer episodeNo,
    Long executionId,
    String status,
    Integer warningCount,
    Integer businessCallCount,
    Integer technicalRetryCount,
    BigDecimal settledPoints,
    String errorCode,
    String errorMessage
) {
}

record StoryboardBatchResponse(
    Long id,
    Long projectId,
    String name,
    String status,
    Integer total,
    Integer pending,
    Integer running,
    Integer succeeded,
    Integer warning,
    Integer failed,
    Integer businessCallCount,
    Integer technicalRetryCount,
    BigDecimal settledPoints,
    List<StoryboardBatchItemResponse> items,
    LocalDateTime createdAt
) {
}
