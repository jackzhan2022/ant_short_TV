package com.antshorttv.points;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiPointReservationMapper extends BaseMapper<AiPointReservationEntity> {
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
