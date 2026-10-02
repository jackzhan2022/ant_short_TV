package com.antshorttv.style;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.common.MediaPage;
import com.antshorttv.common.MediaPageQueries;
import com.antshorttv.common.PageBounds;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StyleLibraryService {
    private final StyleLibraryMapper styleLibraryMapper;
    private final StyleLibraryImageStorage imageStorage;

    public StyleLibraryService(
        StyleLibraryMapper styleLibraryMapper,
        StyleLibraryImageStorage imageStorage
    ) {
        this.styleLibraryMapper = styleLibraryMapper;
        this.imageStorage = imageStorage;
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
                "/api/style-library/images/" + entity.getExternalId(),
                entity.getStoragePath(),
                entity.getImageWidth(),
                entity.getImageHeight()
            ))
            .toList();
    }

    public MediaPage<StyleLibraryResponse> list(String category, String keyword, Integer current, Integer pageSize) {
        var query = new QueryWrapper<StyleLibraryEntity>().eq("is_public", true);
        if (category != null && !category.isBlank()) query.eq("category", category.trim());
        if (keyword != null && !keyword.isBlank()) {
            query.and(q -> q.like("name", keyword.trim()).or().like("description", keyword.trim()));
        }
        return MediaPageQueries.select(styleLibraryMapper, query, PageBounds.of(current, pageSize),
            q -> q.orderByAsc("sort_order", "id")).map(this::response);
    }

    public List<String> categories() {
        return styleLibraryMapper.selectPublicCategories();
    }

    public StyleLibraryResponse detail(Long id) {
        var style = styleLibraryMapper.selectOne(new QueryWrapper<StyleLibraryEntity>()
            .eq("id", id).eq("is_public", true));
        if (style == null) throw new BusinessException(ErrorCode.NOT_FOUND, "风格不存在。");
        return response(style);
    }

    private StyleLibraryResponse response(StyleLibraryEntity entity) {
        return new StyleLibraryResponse(entity.getId(), entity.getExternalId(), entity.getName(), entity.getCategory(),
            entity.getDescription(), "/api/style-library/images/" + entity.getExternalId(), entity.getStoragePath(),
            entity.getImageWidth(), entity.getImageHeight());
    }

    public String image(String externalId) {
        StyleLibraryEntity style = styleLibraryMapper.selectOne(new LambdaQueryWrapper<StyleLibraryEntity>()
            .eq(StyleLibraryEntity::getExternalId, externalId)
            .eq(StyleLibraryEntity::getIsPublic, true)
            .last("limit 1"));
        if (style == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "风格不存在。");
        }
        return imageStorage.deliveryUrl(style);
    }
}
