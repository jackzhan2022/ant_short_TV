package com.antshorttv.video;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiVideoTaskMapper extends BaseMapper<AiVideoTaskEntity> {
    default AiVideoTaskEntity selectActive(Long tenantId, Long projectId, Long id) {
        return selectOne(new QueryWrapper<AiVideoTaskEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("id", id)
            .isNull("deleted_at"));
    }

    default List<AiVideoTaskEntity> selectByProject(Long tenantId, Long projectId, String status, Long storyboardId) {
        QueryWrapper<AiVideoTaskEntity> query = new QueryWrapper<AiVideoTaskEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .isNull("deleted_at")
            .orderByDesc("created_at");
        if (status != null && !status.isBlank()) {
            query.eq("status", status);
        }
        if (storyboardId != null) {
            query.eq("storyboard_id", storyboardId);
        }
        return selectList(query);
    }

    default AiVideoTaskEntity selectActiveDuplicate(Long tenantId, Long projectId, String requestHash) {
        return selectOne(new QueryWrapper<AiVideoTaskEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("request_hash", requestHash)
            .in("status", List.of(
                AiVideoTaskStatus.PENDING.name(),
                AiVideoTaskStatus.SUBMITTING.name(),
                AiVideoTaskStatus.GENERATING.name()
            ))
            .isNull("deleted_at")
            .orderByDesc("created_at")
            .last("limit 1"));
    }

    default Long countActiveByTenant(Long tenantId) {
        return selectCount(new QueryWrapper<AiVideoTaskEntity>()
            .eq("tenant_id", tenantId)
            .in("status", List.of(
                AiVideoTaskStatus.PENDING.name(),
                AiVideoTaskStatus.SUBMITTING.name(),
                AiVideoTaskStatus.GENERATING.name()
            ))
            .isNull("deleted_at"));
    }

    default List<AiVideoTaskEntity> selectDueTasks(LocalDateTime now, int limit) {
        return selectList(new QueryWrapper<AiVideoTaskEntity>()
            .in("status", List.of(
                AiVideoTaskStatus.PENDING.name(),
                AiVideoTaskStatus.SUBMITTING.name(),
                AiVideoTaskStatus.GENERATING.name()
            ))
            .and(query -> query.isNull("next_poll_at").or().le("next_poll_at", now))
            .isNull("deleted_at")
            .orderByAsc("created_at")
            .last("limit " + limit));
    }
}
