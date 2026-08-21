package com.antshorttv.video;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiVideoTaskAttemptMapper extends BaseMapper<AiVideoTaskAttemptEntity> {
    default AiVideoTaskAttemptEntity selectLatest(Long taskId) {
        return selectOne(new QueryWrapper<AiVideoTaskAttemptEntity>()
            .eq("task_id", taskId)
            .orderByDesc("started_at")
            .last("limit 1"));
    }
}
