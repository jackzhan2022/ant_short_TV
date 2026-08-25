package com.antshorttv.accounting;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiUsageCostLineMapper extends BaseMapper<AiUsageCostLineEntity> {
    default AiUsageCostLineEntity selectByUsageLineId(Long usageLineId) {
        return selectOne(new QueryWrapper<AiUsageCostLineEntity>().eq("usage_line_id", usageLineId));
    }

    default List<AiUsageCostLineEntity> selectByExecutionId(Long executionId) {
        return selectList(new QueryWrapper<AiUsageCostLineEntity>()
            .eq("execution_id", executionId)
            .orderByAsc("id"));
    }

    default List<AiUsageCostLineEntity> selectByCallLogId(Long callLogId) {
        return selectList(new QueryWrapper<AiUsageCostLineEntity>()
            .eq("ai_call_log_id", callLogId)
            .orderByAsc("id"));
    }
}
