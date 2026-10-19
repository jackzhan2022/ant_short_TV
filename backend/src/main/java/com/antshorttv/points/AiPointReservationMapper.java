package com.antshorttv.points;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiPointReservationMapper extends BaseMapper<AiPointReservationEntity> {
    @Select("select * from ai_point_reservation where id = #{id} for update")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    AiPointReservationEntity selectByIdForUpdate(Long id);

    default AiPointReservationEntity selectByIdempotency(Long tenantId, String key) {
        return selectOne(new QueryWrapper<AiPointReservationEntity>()
            .eq("tenant_id", tenantId)
            .eq("idempotency_key", key));
    }


    default AiPointReservationEntity selectByExecutionId(Long executionId) {
        return selectOne(new QueryWrapper<AiPointReservationEntity>()
            .eq("execution_id", executionId));
    }
}
