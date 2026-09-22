package com.antshorttv.inspiration;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InspirationManagementService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final InspirationCreationMapper mapper;
    private final InspirationManagementMediaService mediaService;
    private final InspirationCreationMediaStorage mediaStorage;
    private final ObjectMapper objectMapper;

    public InspirationManagementService(
        InspirationCreationMapper mapper,
        InspirationManagementMediaService mediaService,
        InspirationCreationMediaStorage mediaStorage,
        ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.mediaService = mediaService;
        this.mediaStorage = mediaStorage;
        this.objectMapper = objectMapper;
    }

    public InspirationManagementPageResponse list(
        Integer page,
        Integer pageSize,
        String keyword,
        String publishStatus,
        String mediaType
    ) {
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        validateOptionalPublishStatus(publishStatus);
        validateOptionalMediaType(mediaType);
        Long total = mapper.selectCount(managementQuery(keyword, publishStatus, mediaType));
        List<InspirationManagementItemResponse> records = mapper.selectList(
                managementQuery(keyword, publishStatus, mediaType)
                    .orderByAsc(InspirationCreationEntity::getSortOrder)
                    .orderByAsc(InspirationCreationEntity::getId)
                    .last("limit %d offset %d".formatted(size, (current - 1) * size))
            ).stream().map(this::response).toList();
        return new InspirationManagementPageResponse(records, total == null ? 0 : total, current, size);
    }

    @Transactional
    public InspirationManagementItemResponse create(MultipartFile file, InspirationCreateMetadata request) {
        validateMetadata(request.title(), request.promptText());
        String publishStatus = normalizePublishStatus(request.publishStatus());
        String externalId = "manual-" + UUID.randomUUID();
        ManagedInspirationMedia media = mediaService.store(externalId, file);
        try {
            LocalDateTime now = LocalDateTime.now();
            InspirationCreationEntity entity = new InspirationCreationEntity();
            entity.setExternalId(externalId);
            entity.setCreationType(media.creationType());
            entity.setTaskType("MANUAL_UPLOAD");
            entity.setTitle(request.title().trim());
            entity.setAuthorName("管理员");
            entity.setUrl("");
            entity.setStoragePath(media.storagePath());
            entity.setMimeType(media.mimeType());
            entity.setFileSize(media.fileSize());
            entity.setThumbnailPath(media.thumbnailPath());
            entity.setThumbnailUrl("");
            entity.setThumbnailMimeType(media.thumbnailMimeType());
            entity.setThumbnailFileSize(media.thumbnailFileSize());
            entity.setThumbnailStatus("READY");
            entity.setPromptText(request.promptText().trim());
            entity.setTagsJson(writeTags(request.tags()));
            entity.setPublishStatus(publishStatus);
            entity.setSourceType("MANUAL");
            entity.setImportStatus(InspirationCreationImportStatus.IMPORTED.name());
            entity.setSortOrder(nextSortOrder());
            entity.setSourceCreatedAt(now);
            entity.setSourceUpdatedAt(now);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            mapper.insert(entity);
            entity.setUrl("/api/inspiration-creations/%d/file".formatted(entity.getId()));
            entity.setThumbnailUrl("/api/inspiration-creations/%d/thumbnail".formatted(entity.getId()));
            mapper.updateById(entity);
            return response(entity);
        } catch (RuntimeException exception) {
            mediaStorage.delete(media.storagePath());
            mediaStorage.delete(media.thumbnailPath());
            throw exception;
        }
    }

    @Transactional
    public InspirationManagementItemResponse update(Long id, InspirationMetadataRequest request) {
        validateMetadata(request.title(), request.promptText());
        InspirationCreationEntity entity = requireManaged(id);
        entity.setTitle(request.title().trim());
        entity.setPromptText(request.promptText().trim());
        entity.setTagsJson(writeTags(request.tags()));
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        return response(entity);
    }

    @Transactional
    public InspirationManagementItemResponse updatePublishStatus(
        Long id,
        InspirationPublishStatusRequest request
    ) {
        InspirationCreationEntity entity = requireManaged(id);
        entity.setPublishStatus(normalizePublishStatus(request.publishStatus()));
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        return response(entity);
    }

    @Transactional
    public void reorder(InspirationReorderRequest request) {
        List<Long> orderedIds = request == null ? null : request.orderedIds();
        List<InspirationCreationEntity> existing = mapper.selectList(
            new LambdaQueryWrapper<InspirationCreationEntity>()
                .isNull(InspirationCreationEntity::getDeletedAt)
                .orderByAsc(InspirationCreationEntity::getSortOrder)
                .orderByAsc(InspirationCreationEntity::getId)
        );
        if (orderedIds == null || orderedIds.size() != existing.size()) {
            throw validation("排序内容必须包含全部有效灵感。");
        }
        Set<Long> expected = existing.stream().map(InspirationCreationEntity::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> supplied = new HashSet<>(orderedIds);
        if (supplied.size() != orderedIds.size() || !supplied.equals(expected)) {
            throw validation("排序内容包含未知、已删除或重复的灵感。");
        }
        for (int index = 0; index < orderedIds.size(); index++) {
            InspirationCreationEntity entity = mapper.selectById(orderedIds.get(index));
            entity.setSortOrder((index + 1) * 10);
            entity.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(entity);
        }
    }

    @Transactional
    public void delete(Long id) {
        InspirationCreationEntity entity = requireManaged(id);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        mediaStorage.delete(entity.getStoragePath());
        mediaStorage.delete(entity.getThumbnailPath());
    }

    private LambdaQueryWrapper<InspirationCreationEntity> managementQuery(
        String keyword,
        String publishStatus,
        String mediaType
    ) {
        LambdaQueryWrapper<InspirationCreationEntity> query = new LambdaQueryWrapper<InspirationCreationEntity>()
            .isNull(InspirationCreationEntity::getDeletedAt);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(item -> item.like(InspirationCreationEntity::getTitle, value)
                .or().like(InspirationCreationEntity::getPromptText, value));
        }
        if (publishStatus != null && !publishStatus.isBlank()) {
            query.eq(InspirationCreationEntity::getPublishStatus, publishStatus.toUpperCase());
        }
        if (mediaType != null && !mediaType.isBlank()) {
            query.eq(InspirationCreationEntity::getCreationType, mediaType.toUpperCase());
        }
        return query;
    }

    private InspirationCreationEntity requireManaged(Long id) {
        InspirationCreationEntity entity = mapper.selectOne(
            new LambdaQueryWrapper<InspirationCreationEntity>()
                .eq(InspirationCreationEntity::getId, id)
                .isNull(InspirationCreationEntity::getDeletedAt)
                .last("limit 1")
        );
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "灵感内容不存在。");
        }
        return entity;
    }

    private InspirationManagementItemResponse response(InspirationCreationEntity entity) {
        return new InspirationManagementItemResponse(
            entity.getId(), entity.getTitle(), readTags(entity.getTagsJson()), entity.getPromptText(),
            entity.getCreationType(), entity.getMimeType(), entity.getUrl(),
            "READY".equals(entity.getThumbnailStatus()) ? entity.getThumbnailUrl() : null,
            entity.getPublishStatus(), entity.getSourceType(), entity.getSortOrder(),
            entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private int nextSortOrder() {
        List<InspirationCreationEntity> last = mapper.selectList(
            new LambdaQueryWrapper<InspirationCreationEntity>()
                .isNull(InspirationCreationEntity::getDeletedAt)
                .orderByDesc(InspirationCreationEntity::getSortOrder)
                .last("limit 1")
        );
        return last.isEmpty() || last.get(0).getSortOrder() == null ? 10 : last.get(0).getSortOrder() + 10;
    }

    private void validateMetadata(String title, String promptText) {
        if (title == null || title.isBlank() || title.trim().length() > 200) {
            throw validation("标题不能为空且不能超过 200 个字符。");
        }
        if (promptText == null || promptText.isBlank()) {
            throw validation("提示词不能为空。");
        }
    }

    private String normalizePublishStatus(String status) {
        String normalized = status == null || status.isBlank() ? "UNPUBLISHED" : status.toUpperCase();
        if (!Set.of("PUBLISHED", "UNPUBLISHED").contains(normalized)) {
            throw validation("发布状态不合法。");
        }
        return normalized;
    }

    private void validateOptionalPublishStatus(String status) {
        if (status != null && !status.isBlank()) {
            normalizePublishStatus(status);
        }
    }

    private void validateOptionalMediaType(String mediaType) {
        if (mediaType != null && !mediaType.isBlank()
            && !Set.of("IMAGE", "VIDEO").contains(mediaType.toUpperCase())) {
            throw validation("媒体类型不合法。");
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
            throw validation("标签格式不合法。");
        }
    }

    private List<String> readTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
