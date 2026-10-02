package com.antshorttv.storage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface MediaProcessingJobMapper extends BaseMapper<MediaProcessingJobEntity> {
    @Select("""
        select * from media_processing_job
         where output_key = #{outputKey} and operation = #{operation}
         limit 1 for update
        """)
    MediaProcessingJobEntity findForUpdate(
        @Param("outputKey") String outputKey,
        @Param("operation") String operation
    );

    @Select("""
        select * from media_processing_job
         where callback_token_hash = #{tokenHash}
         limit 1 for update
        """)
    MediaProcessingJobEntity findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Update("""
        update media_processing_job
           set tenant_id = #{entity.tenantId}, project_id = #{entity.projectId},
               media_object_id = #{entity.mediaObjectId}, provider_job_id = null,
               queue_id = null, operation = #{entity.operation}, input_key = #{entity.inputKey},
               output_key = #{entity.outputKey},
               callback_token_hash = #{entity.callbackTokenHash},
               correlation_data = #{entity.correlationData}, status = #{entity.status},
               attempt_no = #{entity.attemptNo}, error_code = null, error_message = null,
               submitted_at = null, completed_at = null, updated_at = #{entity.updatedAt}
         where id = #{entity.id}
        """)
    int resetForSubmission(@Param("entity") MediaProcessingJobEntity entity);
}
