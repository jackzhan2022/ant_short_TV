package com.antshorttv.storage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
