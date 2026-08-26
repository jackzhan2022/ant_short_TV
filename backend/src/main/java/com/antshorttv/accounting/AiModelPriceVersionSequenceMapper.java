package com.antshorttv.accounting;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiModelPriceVersionSequenceMapper {
    @Insert("""
        insert into ai_model_price_version_sequence (model_id, price_type, last_version_no)
        select #{modelId}, #{priceType},
               case #{priceType}
                 when 'COST' then coalesce((
                   select max(version_no) from ai_model_price_version where model_id = #{modelId}
                 ), 0) + 1
                 when 'POINT' then coalesce((
                   select max(version_no) from ai_model_point_price_version where model_id = #{modelId}
                 ), 0) + 1
               end
        """)
    int insertNextFromHistory(@Param("modelId") Long modelId, @Param("priceType") String priceType);

    @Select("""
        select last_version_no
          from ai_model_price_version_sequence
         where model_id = #{modelId} and price_type = #{priceType}
         for update
        """)
    Integer selectLastForUpdate(@Param("modelId") Long modelId, @Param("priceType") String priceType);

    @Update("""
        update ai_model_price_version_sequence
           set last_version_no = #{versionNo}
         where model_id = #{modelId} and price_type = #{priceType}
        """)
    int updateLast(
        @Param("modelId") Long modelId,
        @Param("priceType") String priceType,
        @Param("versionNo") Integer versionNo
    );
}
