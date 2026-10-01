package com.antshorttv.inspiration;

import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.VerifiedMediaUpload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class InspirationManagementImageCreationService {
    private final InspirationCreationMapper mapper;
    private final InspirationManagementMediaService mediaService;
    private final ImageDisplayRenditionService imageRenditions;
    private final ObjectMapper objectMapper;

    InspirationManagementImageCreationService(
        InspirationCreationMapper mapper,
        InspirationManagementMediaService mediaService,
        ImageDisplayRenditionService imageRenditions,
        ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.mediaService = mediaService;
        this.imageRenditions = imageRenditions;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public InspirationCreationEntity create(
        String externalId,
        VerifiedMediaUpload upload,
        InspirationCreateUploadRequest request,
        String publishStatus,
        int sortOrder
    ) {
        InspirationCreationEntity entity = processingEntity(
            externalId, upload, request, publishStatus, sortOrder
        );
        mapper.insert(entity);
        try {
            boolean video = "VIDEO".equals(entity.getCreationType());
            ManagedInspirationMedia media = video
                ? mediaService.storeVideoOriginal(entity.getId(), externalId, upload)
                : mediaService.storeImage(entity.getId(), externalId, upload);
            applyMedia(entity, media);
            attach(entity);
            if (video) {
                applyMedia(entity, mediaService.storeVideoCover(entity));
                attach(entity);
            }
            return entity;
        } catch (RuntimeException exception) {
            if ("IMAGE".equals(entity.getCreationType())) {
                try {
                    imageRenditions.retire(InspirationImageRenditionReconciler.identity(entity));
                } catch (RuntimeException retireFailure) {
                    exception.addSuppressed(retireFailure);
                }
            }
            failHidden(entity, exception);
            throw exception;
        }
    }

    private InspirationCreationEntity processingEntity(
        String externalId,
        VerifiedMediaUpload upload,
        InspirationCreateUploadRequest request,
        String publishStatus,
        int sortOrder
    ) {
        LocalDateTime now = LocalDateTime.now();
        InspirationCreationEntity entity = new InspirationCreationEntity();
        entity.setExternalId(externalId);
        entity.setCreationType("video/mp4".equalsIgnoreCase(upload.contentType()) ? "VIDEO" : "IMAGE");
        entity.setTaskType("MANUAL_UPLOAD");
        entity.setTitle(request.title().trim());
        entity.setAuthorName("管理员");
        entity.setUrl("");
        entity.setStoragePath("");
        entity.setMimeType(upload.contentType());
        entity.setFileSize(upload.size());
        entity.setThumbnailStatus("PENDING");
        entity.setPromptText(request.promptText().trim());
        entity.setTagsJson(writeTags(request.tags()));
        entity.setPublishStatus(publishStatus);
        entity.setSourceType("MANUAL");
        entity.setImportStatus(InspirationCreationImportStatus.PROCESSING.name());
        entity.setSortOrder(sortOrder);
        entity.setSourceCreatedAt(now);
        entity.setSourceUpdatedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private void applyMedia(InspirationCreationEntity entity, ManagedInspirationMedia media) {
        entity.setStoragePath(media.storagePath());
        entity.setMimeType(media.mimeType());
        entity.setFileSize(media.fileSize());
        entity.setThumbnailPath(media.thumbnailPath());
        entity.setThumbnailMimeType(media.thumbnailMimeType());
        entity.setThumbnailFileSize(null);
        entity.setThumbnailStatus("PENDING");
        entity.setThumbnailError(null);
        entity.setImportError(null);
        entity.setUrl("/api/inspiration-creations/%d/file".formatted(entity.getId()));
        entity.setThumbnailUrl(
            "/api/inspiration-creations/%d/thumbnail".formatted(entity.getId())
        );
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private void attach(InspirationCreationEntity entity) {
        if (mapper.attachMediaIfActive(entity) != 1) {
            throw new IllegalStateException("灵感素材处理状态写入失败。");
        }
    }

    private void failHidden(InspirationCreationEntity entity, RuntimeException exception) {
        try {
            String error = bounded(exception.getMessage());
            entity.setImportStatus(InspirationCreationImportStatus.FAILED.name());
            entity.setImportError(error);
            entity.setThumbnailStatus("FAILED");
            entity.setThumbnailError(error);
            entity.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(entity);
        } catch (RuntimeException ignored) {
            // The already-persisted PROCESSING row remains hidden if the database is unavailable.
        }
    }

    private String writeTags(List<String> tags) {
        try {
            List<String> normalized = tags == null ? List.of() : tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .limit(10)
                .toList();
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception exception) {
            throw new IllegalArgumentException("标签格式不合法。", exception);
        }
    }

    private String bounded(String value) {
        String message = value == null || value.isBlank() ? "灵感图片处理失败。" : value;
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
