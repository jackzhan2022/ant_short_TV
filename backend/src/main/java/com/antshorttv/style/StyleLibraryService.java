package com.antshorttv.style;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.ObjectStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class StyleLibraryService {
    private final StyleLibraryMapper styleLibraryMapper;
    private final StyleLibraryImageStorage imageStorage;
    private final ObjectStorageService objectStorageService;

    public StyleLibraryService(
        StyleLibraryMapper styleLibraryMapper,
        StyleLibraryImageStorage imageStorage,
        ObjectStorageService objectStorageService
    ) {
        this.styleLibraryMapper = styleLibraryMapper;
        this.imageStorage = imageStorage;
        this.objectStorageService = objectStorageService;
    }

    public List<StyleLibraryResponse> list(String category, String keyword) {
        LambdaQueryWrapper<StyleLibraryEntity> wrapper = new LambdaQueryWrapper<StyleLibraryEntity>()
            .eq(StyleLibraryEntity::getIsPublic, true);
        if (category != null && !category.isBlank()) {
            wrapper.eq(StyleLibraryEntity::getCategory, category.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(query -> query
                .like(StyleLibraryEntity::getName, value)
                .or()
                .like(StyleLibraryEntity::getDescription, value));
        }
        return styleLibraryMapper.selectList(wrapper
                .orderByAsc(StyleLibraryEntity::getSortOrder)
                .orderByAsc(StyleLibraryEntity::getId))
            .stream()
            .map(entity -> new StyleLibraryResponse(
                entity.getId(),
                entity.getExternalId(),
                entity.getName(),
                entity.getCategory(),
                entity.getDescription(),
                objectStorageService.publicUrl(entity.getStoragePath()),
                entity.getStoragePath(),
                entity.getImageWidth(),
                entity.getImageHeight()
            ))
            .toList();
    }

    public Resource image(String externalId) {
        StyleLibraryEntity style = styleLibraryMapper.selectOne(new LambdaQueryWrapper<StyleLibraryEntity>()
            .eq(StyleLibraryEntity::getExternalId, externalId)
            .eq(StyleLibraryEntity::getIsPublic, true)
            .last("limit 1"));
        if (style == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "风格不存在。");
        }
        return imageStorage.resource(style);
    }
}
