package com.antshorttv.storage;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
interface MediaObjectMapper {
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
