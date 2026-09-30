package com.antshorttv.storage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface MediaUploadSessionMapper extends BaseMapper<MediaUploadSessionEntity> {
    @Select("select * from media_upload_session where session_token = #{token} for update")
    MediaUploadSessionEntity findForUpdate(@Param("token") String token);
}
