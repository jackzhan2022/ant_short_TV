package com.antshorttv.storage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface MediaObjectMapper extends BaseMapper<MediaObjectEntity> {
    @Select("""
        select * from media_object
         where tenant_id = #{identity.tenantId}
           and asset_type = #{identity.assetType}
           and asset_id = #{identity.assetId}
           and version_id = #{identity.versionId}
           and rendition_type = #{renditionType}
         limit 1 for update
        """)
    MediaObjectEntity findForUpdate(
        @Param("identity") MediaObjectIdentity identity,
        @Param("renditionType") String renditionType
    );

    @Update("""
        update media_object
           set file_size = #{size}, etag = #{eTag}, mime_type = #{mimeType},
               width = #{width}, height = #{height}, status = 'READY',
               error_message = null, updated_at = current_timestamp
         where id = #{id}
        """)
    int markReady(
        @Param("id") Long id,
        @Param("size") long size,
        @Param("eTag") String eTag,
        @Param("mimeType") String mimeType,
        @Param("width") int width,
        @Param("height") int height
    );

    @Update("""
        update media_object
           set status = 'FAILED', error_message = #{message}, updated_at = current_timestamp
         where id = #{id}
        """)
    int markFailed(@Param("id") Long id, @Param("message") String message);
}
