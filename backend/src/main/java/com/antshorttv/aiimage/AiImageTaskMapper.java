package com.antshorttv.aiimage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiImageTaskMapper extends BaseMapper<AiImageTaskEntity> {
    default List<AiImageTaskEntity> selectByProject(Long tenantId, Long projectId, String taskType, String status) {
        LambdaQueryWrapper<AiImageTaskEntity> wrapper = new LambdaQueryWrapper<AiImageTaskEntity>()
            .eq(AiImageTaskEntity::getTenantId, tenantId)
            .eq(AiImageTaskEntity::getProjectId, projectId)
            .isNull(AiImageTaskEntity::getDeletedAt);
        if (taskType != null && !taskType.isBlank()) {
            wrapper.eq(AiImageTaskEntity::getTaskType, taskType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(AiImageTaskEntity::getStatus, status);
        }
        return selectList(wrapper.orderByDesc(AiImageTaskEntity::getCreatedAt));
    }
}
