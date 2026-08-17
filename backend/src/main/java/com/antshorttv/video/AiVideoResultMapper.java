package com.antshorttv.video;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiVideoResultMapper extends BaseMapper<AiVideoResultEntity> {
    default AiVideoResultEntity selectActive(Long tenantId, Long projectId, Long id) {
        return selectOne(new QueryWrapper<AiVideoResultEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("id", id)
            .eq("status", AiVideoResultStatus.ACTIVE.name()));
    }

    default List<AiVideoResultEntity> selectByTask(Long tenantId, Long projectId, Long taskId) {
        return selectList(new QueryWrapper<AiVideoResultEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("task_id", taskId)
            .eq("status", AiVideoResultStatus.ACTIVE.name())
            .orderByAsc("id"));
    }
}
