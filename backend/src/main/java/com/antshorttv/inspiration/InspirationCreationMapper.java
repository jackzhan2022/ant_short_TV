package com.antshorttv.inspiration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
}
