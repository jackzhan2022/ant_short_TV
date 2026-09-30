package com.antshorttv.aiimage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiImageResultMapper extends BaseMapper<AiImageResultEntity> {
    @Update("""
        update ai_image_result
           set status = 'ACTIVE', updated_at = current_timestamp
         where task_id = #{taskId} and execution_id = #{executionId} and status = 'PROCESSING'
        """)
    int activateForPublication(
        @Param("taskId") Long taskId,
        @Param("executionId") Long executionId
    );

    default List<AiImageResultEntity> selectByTask(Long taskId) {
        return selectList(new LambdaQueryWrapper<AiImageResultEntity>()
            .eq(AiImageResultEntity::getTaskId, taskId)
            .ne(AiImageResultEntity::getStatus, AiImageResultStatus.DELETED.name())
            .orderByAsc(AiImageResultEntity::getId));
    }

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
