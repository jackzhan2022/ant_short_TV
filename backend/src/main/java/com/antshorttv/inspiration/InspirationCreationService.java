package com.antshorttv.inspiration;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.ObjectStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class InspirationCreationService {
    private static final String IMPORTED = "IMPORTED";

    private final InspirationCreationMapper inspirationCreationMapper;
    private final ObjectStorageService objectStorageService;

    public InspirationCreationService(
        InspirationCreationMapper inspirationCreationMapper,
        ObjectStorageService objectStorageService
    ) {
        this.inspirationCreationMapper = inspirationCreationMapper;
        this.objectStorageService = objectStorageService;
    }

    public List<InspirationCreationResponse> list() {
        return inspirationCreationMapper.selectList(new LambdaQueryWrapper<InspirationCreationEntity>()
                .eq(InspirationCreationEntity::getImportStatus, IMPORTED)
                .orderByAsc(InspirationCreationEntity::getSortOrder)
                .orderByAsc(InspirationCreationEntity::getId))
            .stream()
            .map(entity -> new InspirationCreationResponse(
                entity.getId(),
                entity.getExternalId(),
                entity.getCreationType(),
                entity.getTaskType(),
                entity.getTitle(),
                entity.getAuthorName(),
                objectStorageService.publicUrl(entity.getStoragePath()),
                entity.getMimeType(),
                entity.getSortOrder(),
                entity.getSourceCreatedAt()
            ))
            .toList();
    }

    public InspirationCreationDetailResponse detail(String externalId) {
        InspirationCreationEntity entity = findImported(externalId);
        return new InspirationCreationDetailResponse(
            entity.getId(),
            entity.getExternalId(),
            entity.getCreationType(),
            entity.getTaskType(),
            entity.getTitle(),
            entity.getAuthorName(),
            objectStorageService.publicUrl(entity.getStoragePath()),
            entity.getMimeType(),
            entity.getSortOrder(),
            entity.getSourceCreatedAt(),
            entity.getDetailJson()
        );
    }

    public InspirationCreationEntity findImported(String externalId) {
        InspirationCreationEntity inspiration = inspirationCreationMapper.selectOne(new LambdaQueryWrapper<InspirationCreationEntity>()
            .eq(InspirationCreationEntity::getExternalId, externalId)
            .eq(InspirationCreationEntity::getImportStatus, IMPORTED)
            .last("limit 1"));
        if (inspiration == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "灵感内容不存在。");
        }
        return inspiration;
    }

    public Resource media(String externalId) {
        return objectStorageService.resource(findImported(externalId).getStoragePath());
    }
}
