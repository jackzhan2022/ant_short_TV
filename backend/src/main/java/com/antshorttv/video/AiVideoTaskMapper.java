package com.antshorttv.video;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiVideoTaskMapper extends BaseMapper<AiVideoTaskEntity> {
    String[] LIST_QUERY_COLUMNS = {
        "id",
        "tenant_id",
        "project_id",
        "storyboard_id",
        "service_config_id",
        "provider_code",
        "model",
        "prompt",
        "negative_prompt",
        "first_frame_image_id",
        "first_frame_url",
        "last_frame_image_id",
        "last_frame_url",
        "reference_images",
        "duration_seconds",
        "aspect_ratio",
        "resolution",
        "motion_strength",
        "camera_movement",
        "random_seed",
        "external_task_id",
        "external_status",
        "status",
        "error_message",
        "submitted_at",
        "started_at",
        "completed_at",
        "created_by",
        "created_at",
        "updated_at",
        "deleted_at"
    };

    default AiVideoTaskEntity selectActive(Long tenantId, Long projectId, Long id) {
        return selectOne(new QueryWrapper<AiVideoTaskEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("id", id)
            .isNull("deleted_at"));
    }

    default List<AiVideoTaskEntity> selectByProject(Long tenantId, Long projectId, String status, Long storyboardId) {
        QueryWrapper<AiVideoTaskEntity> query = new QueryWrapper<AiVideoTaskEntity>()
            .select(LIST_QUERY_COLUMNS)
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
