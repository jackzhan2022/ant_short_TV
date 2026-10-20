package com.antshorttv.execution;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiExecutionTaskMapper extends BaseMapper<AiExecutionTaskEntity> {
    // The domain state also routes tasks deferred by releases predating the publication executor.
    String IMAGE_PUBLICATION = """
        (ai_execution_task.scene = 'ai_image_generate' and exists (
            select 1 from ai_image_task image
             where image.execution_id = ai_execution_task.id
               and image.id = ai_execution_task.business_id
               and image.tenant_id = ai_execution_task.tenant_id
               and image.project_id = ai_execution_task.project_id
               and image.deleted_at is null
               and image.status in ('SETTLING', 'RENDERING', 'SUCCESS')
        ))
        """;

    @Select("select count(*) from ai_execution_task where id = #{executionId} and " + IMAGE_PUBLICATION)
    long countImagePublication(@Param("executionId") Long executionId);

    @Select("""
        select * from ai_execution_task
         where id = #{executionId} and status = 'RUNNING' and claim_token = #{claimToken}
         for update
        """)
    AiExecutionTaskEntity lockActiveClaim(
        @Param("executionId") Long executionId,
        @Param("claimToken") String claimToken
    );
}
