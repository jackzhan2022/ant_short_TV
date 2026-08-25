package com.antshorttv.script;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScriptAiOperationMapper extends BaseMapper<ScriptAiOperationEntity> {
    default ScriptAiOperationEntity selectByIdempotency(Long tenantId, String operationType, String key) {
        return selectOne(new QueryWrapper<ScriptAiOperationEntity>()
            .eq("tenant_id", tenantId)
            .eq("operation_type", operationType)
            .eq("idempotency_key", key));
    }
}
