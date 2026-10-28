package com.antshorttv.downloadcenter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public record DownloadTask(String id, String name, String sourceType, String status, int fileCount,
    Long fileSize, String errorMessage, Instant createdAt, Instant completedAt, Instant expiresAt, String outputFormat, String fileName, String contentType) {
    public record Source(@NotBlank String kind, @Positive Long projectId, @NotNull @Positive Long id, @Positive Long parentId) {}
    public record Request(@NotBlank @Size(max=160) String name, @NotEmpty @Size(max=1000) List<@Valid Source> sources) {}
    public record Page(List<DownloadTask> items, long total, int page, int pageSize) {}
}
