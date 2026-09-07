package com.antshorttv.inspiration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InspirationCreationMapper extends BaseMapper<InspirationCreationEntity> {
    default InspirationCreationEntity selectByExternalId(String externalId) {
        return selectOne(new LambdaQueryWrapper<InspirationCreationEntity>()
            .eq(InspirationCreationEntity::getExternalId, externalId)
            .last("limit 1"));
    }

    default InspirationCreationEntity selectImportedById(Long id) {
        return selectOne(new LambdaQueryWrapper<InspirationCreationEntity>()
            .eq(InspirationCreationEntity::getId, id)
            .eq(InspirationCreationEntity::getImportStatus, InspirationCreationImportStatus.IMPORTED.name())
            .last("limit 1"));
    }

    default List<InspirationCreationEntity> selectThumbnailBackfillCandidates(int limit) {
        return selectList(new LambdaQueryWrapper<InspirationCreationEntity>()
            .eq(InspirationCreationEntity::getImportStatus, InspirationCreationImportStatus.IMPORTED.name())
            .and(query -> query.isNull(InspirationCreationEntity::getThumbnailStatus)
                .or()
                .ne(InspirationCreationEntity::getThumbnailStatus, "READY"))
            .orderByAsc(InspirationCreationEntity::getId)
            .last("limit %d".formatted(limit)));
    }
}
