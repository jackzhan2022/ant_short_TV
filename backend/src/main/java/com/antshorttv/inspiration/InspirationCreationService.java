package com.antshorttv.inspiration;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.security.CurrentPrincipal;
import com.antshorttv.storage.DeliveryGrant;
import com.antshorttv.storage.DeliveryGrantRequest;
import com.antshorttv.storage.MediaDeliveryGrantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InspirationCreationService {
    private static final int DEFAULT_PAGE_SIZE = 8;
    private static final int MAX_PAGE_SIZE = 40;
    private final InspirationCreationMapper mapper;
    private final InspirationCreationMediaStorage mediaStorage;
    private final ObjectMapper objectMapper;
    private final CurrentPrincipal currentPrincipal;
    private final MediaDeliveryGrantService deliveryGrants;

    public InspirationCreationService(
        InspirationCreationMapper mapper,
        InspirationCreationMediaStorage mediaStorage,
        ObjectMapper objectMapper,
        CurrentPrincipal currentPrincipal,
        MediaDeliveryGrantService deliveryGrants
    ) {
        this.mapper = mapper;
        this.mediaStorage = mediaStorage;
        this.objectMapper = objectMapper;
        this.currentPrincipal = currentPrincipal;
        this.deliveryGrants = deliveryGrants;
    }

    public InspirationCreationPageResponse list(Integer page, Integer pageSize) {
        currentPrincipal.require();
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1
            ? DEFAULT_PAGE_SIZE
            : Math.min(pageSize, MAX_PAGE_SIZE);
        Long total = mapper.selectCount(importedQuery());
        List<InspirationCreationListResponse> records = mapper.selectList(importedQuery()
                .orderByAsc(InspirationCreationEntity::getSortOrder)
                .orderByAsc(InspirationCreationEntity::getId)
                .last("limit %d offset %d".formatted(safePageSize, (safePage - 1) * safePageSize)))
            .stream()
            .map(entity -> InspirationCreationListResponse.from(entity, tags(entity)))
            .toList();
        return new InspirationCreationPageResponse(records, total == null ? 0 : total, safePage, safePageSize);
    }

    public InspirationCreationDetailResponse detail(Long id) {
        currentPrincipal.require();
        InspirationCreationEntity entity = requireImported(id);
        return InspirationCreationDetailResponse.from(entity, tags(entity), detailJson(entity));
    }

    public DeliveryGrant file(Long id) {
        InspirationCreationEntity entity = requireImported(id);
        return grant(entity, entity.getStoragePath(), "ORIGINAL", "VIDEO".equals(entity.getCreationType()));
    }

    public DeliveryGrant thumbnail(Long id) {
        InspirationCreationEntity entity = requireReadyThumbnail(id);
        return grant(entity, entity.getThumbnailPath(), "THUMBNAIL", false);
    }

    private DeliveryGrant grant(
        InspirationCreationEntity entity,
        String objectKey,
        String renditionType,
        boolean video
    ) {
        Long userId = currentPrincipal.require().userId();
        String versionId = entity.getExternalId() == null
            ? "inspiration-" + entity.getId() : entity.getExternalId();
        return deliveryGrants.issue(new DeliveryGrantRequest(
            0L,
            null,
            userId,
            "INSPIRATION_CREATION",
            entity.getId(),
            versionId,
            renditionType,
            objectKey,
            video
        ));
    }

    private InspirationCreationEntity requireImported(Long id) {
        InspirationCreationEntity entity = mapper.selectImportedById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "灵感案例不存在。");
        }
        return entity;
    }

    private InspirationCreationEntity requireReadyThumbnail(Long id) {
        InspirationCreationEntity entity = requireImported(id);
        if (!"READY".equals(entity.getThumbnailStatus())
            || entity.getThumbnailPath() == null
            || entity.getThumbnailPath().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "灵感缩略图不存在。");
        }
        return entity;
    }

    private LambdaQueryWrapper<InspirationCreationEntity> importedQuery() {
        return new LambdaQueryWrapper<InspirationCreationEntity>()
            .eq(InspirationCreationEntity::getImportStatus, InspirationCreationImportStatus.IMPORTED.name())
            .eq(InspirationCreationEntity::getPublishStatus, "PUBLISHED")
            .isNull(InspirationCreationEntity::getDeletedAt);
    }

    private JsonNode detailJson(InspirationCreationEntity entity) {
        if (entity.getDetailJson() == null || entity.getDetailJson().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(entity.getDetailJson());
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> tags(InspirationCreationEntity entity) {
        if (entity.getTagsJson() == null || entity.getTagsJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                entity.getTagsJson(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (Exception exception) {
            return List.of();
        }
    }
}
