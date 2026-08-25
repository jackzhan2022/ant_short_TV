package com.antshorttv.execution;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiExecutionAttemptMapper extends BaseMapper<AiExecutionAttemptEntity> {
    default List<AiExecutionAttemptEntity> selectByExecutionId(Long executionId) {
        return selectList(new QueryWrapper<AiExecutionAttemptEntity>()
            .eq("execution_id", executionId)
            .orderByAsc("attempt_no"));
    }
}
