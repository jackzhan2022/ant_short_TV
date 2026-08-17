package com.antshorttv.aiimage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiImageResultMapper extends BaseMapper<AiImageResultEntity> {
    default List<AiImageResultEntity> selectActiveByTask(Long taskId) {
        return selectList(new LambdaQueryWrapper<AiImageResultEntity>()
            .eq(AiImageResultEntity::getTaskId, taskId)
            .eq(AiImageResultEntity::getStatus, AiImageResultStatus.ACTIVE.name())
            .orderByAsc(AiImageResultEntity::getId));
    }

    default List<AiImageResultEntity> selectActiveByTarget(Long tenantId, Long projectId, String targetType, Long targetId) {
        return selectList(new LambdaQueryWrapper<AiImageResultEntity>()
            .eq(AiImageResultEntity::getTenantId, tenantId)
            .eq(AiImageResultEntity::getProjectId, projectId)
            .eq(AiImageResultEntity::getTargetType, targetType)
            .eq(AiImageResultEntity::getTargetId, targetId)
            .eq(AiImageResultEntity::getStatus, AiImageResultStatus.ACTIVE.name()));
    }
}
