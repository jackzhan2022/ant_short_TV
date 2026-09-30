package com.antshorttv.execution;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiExecutionTaskMapper extends BaseMapper<AiExecutionTaskEntity> {
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
