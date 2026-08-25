package com.antshorttv.points;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiPointLedgerMapper extends BaseMapper<AiPointLedgerEntity> {
    default List<AiPointLedgerEntity> selectByExecutionId(Long executionId) {
        return selectList(new QueryWrapper<AiPointLedgerEntity>()
            .eq("execution_id", executionId)
            .orderByAsc("id"));
    }

    default AiPointLedgerEntity selectLatest(Long tenantId) {
        return selectOne(new QueryWrapper<AiPointLedgerEntity>()
            .eq("tenant_id", tenantId)
            .orderByDesc("id")
            .last("limit 1"));
    }
}
