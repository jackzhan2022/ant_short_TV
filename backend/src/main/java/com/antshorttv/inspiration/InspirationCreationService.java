package com.antshorttv.inspiration;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.security.CurrentPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class InspirationCreationService {
    private static final int DEFAULT_PAGE_SIZE = 8;
    private static final int MAX_PAGE_SIZE = 40;
    private final InspirationCreationMapper mapper;
    private final InspirationCreationMediaStorage mediaStorage;
    private final ObjectMapper objectMapper;
    private final CurrentPrincipal currentPrincipal;

    public InspirationCreationService(
        InspirationCreationMapper mapper,
        InspirationCreationMediaStorage mediaStorage,
        ObjectMapper objectMapper,
        CurrentPrincipal currentPrincipal
    ) {
        this.mapper = mapper;
        this.mediaStorage = mediaStorage;
        this.objectMapper = objectMapper;
        this.currentPrincipal = currentPrincipal;
    }

    public InspirationCreationPageResponse list(Integer page, Integer pageSize) {
        currentPrincipal.require();
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1
            ? DEFAULT_PAGE_SIZE
            : Math.min(pageSize, MAX_PAGE_SIZE);
        LambdaQueryWrapper<InspirationCreationEntity> query = importedQuery();
        Long total = mapper.selectCount(query);
        List<InspirationCreationListResponse> records = mapper.selectList(importedQuery()
                .last("limit %d offset %d".formatted(safePageSize, (safePage - 1) * safePageSize)))
            .stream()
            .map(InspirationCreationListResponse::from)
            .toList();
        return new InspirationCreationPageResponse(records, total == null ? 0 : total, safePage, safePageSize);
    }

    public InspirationCreationDetailResponse detail(Long id) {
        currentPrincipal.require();
        InspirationCreationEntity entity = requireImported(id);
        return InspirationCreationDetailResponse.from(entity, detailJson(entity));
    }

    public Resource file(Long id) {
        currentPrincipal.require();
        return mediaStorage.resource(requireImported(id));
    }

    public String contentType(Long id) {
        currentPrincipal.require();
        InspirationCreationEntity entity = requireImported(id);
        return InspirationCreationMediaStorage.contentType(entity.getStoragePath(), entity.getMimeType());
    }

    private InspirationCreationEntity requireImported(Long id) {
        InspirationCreationEntity entity = mapper.selectImportedById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "灵感案例不存在。");
        }
        return entity;
    }

    private LambdaQueryWrapper<InspirationCreationEntity> importedQuery() {
        return new LambdaQueryWrapper<InspirationCreationEntity>()
            .eq(InspirationCreationEntity::getImportStatus, InspirationCreationImportStatus.IMPORTED.name())
            .orderByAsc(InspirationCreationEntity::getSortOrder)
            .orderByAsc(InspirationCreationEntity::getId);
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
}
