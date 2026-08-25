package com.antshorttv.accounting;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiUsageLineMapper extends BaseMapper<AiUsageLineEntity> {
    default List<AiUsageLineEntity> selectByExecutionId(Long executionId) {
        return selectList(new QueryWrapper<AiUsageLineEntity>()
            .eq("execution_id", executionId)
            .orderByAsc("id"));
    }
}
