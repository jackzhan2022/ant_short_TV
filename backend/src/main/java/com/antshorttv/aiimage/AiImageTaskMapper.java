package com.antshorttv.aiimage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiImageTaskMapper extends BaseMapper<AiImageTaskEntity> {
    @Update("""
        update ai_image_task
           set status = 'FAILED', error_message = #{message}, ai_call_log_id = #{callLogId},
               completed_at = now(), updated_at = now()
         where id = #{taskId} and execution_id = #{executionId} and deleted_at is null
           and exists (select 1 from ai_execution_task e
             where e.id = #{executionId} and e.status = 'RUNNING' and e.claim_token = #{claimToken})
        """)
    int markFailedIfClaimActive(
        @Param("taskId") Long taskId,
        @Param("executionId") Long executionId,
        @Param("claimToken") String claimToken,
        @Param("message") String message,
        @Param("callLogId") Long callLogId
    );

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

    default AiImageTaskEntity selectByIdempotency(Long tenantId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapper<AiImageTaskEntity>()
            .eq(AiImageTaskEntity::getTenantId, tenantId)
            .eq(AiImageTaskEntity::getClientIdempotencyKey, idempotencyKey)
            .isNull(AiImageTaskEntity::getDeletedAt));
    }
}
