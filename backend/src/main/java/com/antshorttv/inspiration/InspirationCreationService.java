package com.antshorttv.inspiration;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.security.CurrentUserHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class InspirationCreationService {
    private final InspirationCreationMapper mapper;
    private final InspirationCreationMediaStorage mediaStorage;
    private final ObjectMapper objectMapper;

    public InspirationCreationService(
        InspirationCreationMapper mapper,
        InspirationCreationMediaStorage mediaStorage,
        ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.mediaStorage = mediaStorage;
        this.objectMapper = objectMapper;
    }

    public List<InspirationCreationListResponse> list() {
        CurrentUserHolder.require();
        return mapper.selectList(new LambdaQueryWrapper<InspirationCreationEntity>()
                .eq(InspirationCreationEntity::getImportStatus, InspirationCreationImportStatus.IMPORTED.name())
                .orderByAsc(InspirationCreationEntity::getSortOrder)
                .orderByAsc(InspirationCreationEntity::getId))
            .stream()
            .map(InspirationCreationListResponse::from)
            .toList();
    }

    public InspirationCreationDetailResponse detail(Long id) {
        CurrentUserHolder.require();
        InspirationCreationEntity entity = requireImported(id);
        return InspirationCreationDetailResponse.from(entity, detailJson(entity));
    }

    public Resource file(Long id) {
        CurrentUserHolder.require();
        return mediaStorage.resource(requireImported(id));
    }

    public String contentType(Long id) {
        CurrentUserHolder.require();
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
