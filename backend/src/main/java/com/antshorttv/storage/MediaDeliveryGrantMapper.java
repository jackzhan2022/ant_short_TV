package com.antshorttv.storage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface MediaDeliveryGrantMapper extends BaseMapper<MediaDeliveryGrantEntity> {

    @Select("""
        select * from media_delivery_grant
         where user_id = #{userId} and object_key_hash = #{objectKeyHash}
         for update
        """)
    MediaDeliveryGrantEntity findForUpdate(
        @Param("userId") Long userId,
        @Param("objectKeyHash") String objectKeyHash
    );
}
