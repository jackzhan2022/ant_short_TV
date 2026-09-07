package com.antshorttv.inspiration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class InspirationThumbnailBackfillService {
    private final InspirationCreationMapper mapper;
    private final InspirationCreationMediaStorage mediaStorage;
    private final InspirationThumbnailProcessor thumbnailProcessor;
    private final InspirationThumbnailProperties properties;

    public InspirationThumbnailBackfillService(
        InspirationCreationMapper mapper,
        InspirationCreationMediaStorage mediaStorage,
        InspirationThumbnailProcessor thumbnailProcessor,
        InspirationThumbnailProperties properties
    ) {
        this.mapper = mapper;
        this.mediaStorage = mediaStorage;
        this.thumbnailProcessor = thumbnailProcessor;
        this.properties = properties;
    }

    public InspirationThumbnailBackfillResult backfill(Integer requestedLimit) {
        int limit = requestedLimit == null || requestedLimit < 1
            ? properties.getBackfillBatchSize()
            : Math.min(requestedLimit, properties.getBackfillBatchSize());
        int processed = 0;
        int failed = 0;
        for (InspirationCreationEntity entity : mapper.selectThumbnailBackfillCandidates(limit)) {
            try {
                storeThumbnail(entity);
                processed++;
            } catch (Exception exception) {
                entity.setThumbnailStatus("FAILED");
                entity.setThumbnailError(message(exception));
                entity.setUpdatedAt(LocalDateTime.now());
                mapper.updateById(entity);
                failed++;
            }
        }
        return new InspirationThumbnailBackfillResult(processed, failed);
    }

    private void storeThumbnail(InspirationCreationEntity entity) throws Exception {
        byte[] original;
        Resource resource = mediaStorage.resource(entity);
        try (InputStream input = resource.getInputStream()) {
            original = input.readAllBytes();
        }
        InspirationThumbnail thumbnail = thumbnail(entity.getMimeType(), original);
        String path = InspirationCreationMediaStorage.thumbnailPath(entity.getExternalId());
        mediaStorage.uploadThumbnail(path, thumbnail);
        entity.setThumbnailPath(path);
        entity.setThumbnailUrl("/api/inspiration-creations/%d/thumbnail".formatted(entity.getId()));
        entity.setThumbnailMimeType(thumbnail.mimeType());
        entity.setThumbnailFileSize((long) thumbnail.bytes().length);
        entity.setThumbnailStatus("READY");
        entity.setThumbnailError(null);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    private InspirationThumbnail thumbnail(String mimeType, byte[] original) throws Exception {
        if (mimeType == null || !mimeType.startsWith("video/")) {
            return thumbnailProcessor.fromImage(original, mimeType);
        }
        Path video = Files.createTempFile("inspiration-thumbnail-", ".mp4");
        try {
            Files.write(video, original);
            return thumbnailProcessor.fromVideo(video);
        } finally {
            Files.deleteIfExists(video);
        }
    }

    private String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}

record InspirationThumbnailBackfillResult(int processed, int failed) {
}
