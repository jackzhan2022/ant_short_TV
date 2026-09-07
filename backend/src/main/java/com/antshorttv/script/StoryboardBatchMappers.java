package com.antshorttv.script;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
interface StoryboardBatchMapper extends BaseMapper<StoryboardBatchEntity> {
    default StoryboardBatchEntity selectByIdempotency(
        Long tenantId, Long projectId, String idempotencyKey
    ) {
        return selectOne(new QueryWrapper<StoryboardBatchEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("idempotency_key", idempotencyKey));
    }

    default StoryboardBatchEntity selectLatest(Long tenantId, Long projectId) {
        return selectOne(new QueryWrapper<StoryboardBatchEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .orderByDesc("created_at", "id")
            .last("limit 1"));
    }

    default StoryboardBatchEntity selectScoped(Long tenantId, Long projectId, Long batchId) {
        return selectOne(new QueryWrapper<StoryboardBatchEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("id", batchId));
    }
}

@Mapper
interface StoryboardBatchItemMapper extends BaseMapper<StoryboardBatchItemEntity> {
    default List<StoryboardBatchItemEntity> selectByBatch(Long tenantId, Long projectId, Long batchId) {
        return selectList(new QueryWrapper<StoryboardBatchItemEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("batch_id", batchId)
            .orderByAsc("episode_no", "id"));
    }
}
