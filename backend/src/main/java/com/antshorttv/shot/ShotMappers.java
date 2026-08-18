package com.antshorttv.shot;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
interface AiVoiceTaskMapper extends BaseMapper<AiVoiceTaskEntity> {
    default AiVoiceTaskEntity selectActive(Long tenantId, Long projectId, Long id) {
        return selectOne(new QueryWrapper<AiVoiceTaskEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("id", id)
            .isNull("deleted_at"));
    }

    default List<AiVoiceTaskEntity> selectByProject(Long tenantId, Long projectId, String status, Long storyboardId) {
        QueryWrapper<AiVoiceTaskEntity> query = new QueryWrapper<AiVoiceTaskEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .isNull("deleted_at")
            .orderByDesc("id");
        if (status != null && !status.isBlank()) {
            query.eq("status", status);
        }
        if (storyboardId != null) {
            query.eq("storyboard_id", storyboardId);
        }
        return selectList(query);
    }
}

@Mapper
interface AiVoiceResultMapper extends BaseMapper<AiVoiceResultEntity> {
    default AiVoiceResultEntity selectActive(Long tenantId, Long projectId, Long id) {
        return selectOne(new QueryWrapper<AiVoiceResultEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("id", id)
            .eq("status", ShotResultStatus.ACTIVE.name()));
    }

    default List<AiVoiceResultEntity> selectByTask(Long tenantId, Long projectId, Long taskId) {
        return selectList(new QueryWrapper<AiVoiceResultEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("task_id", taskId)
            .eq("status", ShotResultStatus.ACTIVE.name())
            .orderByAsc("id"));
    }
}

@Mapper
interface StoryboardSubtitleMapper extends BaseMapper<StoryboardSubtitleEntity> {
    default StoryboardSubtitleEntity selectActive(Long tenantId, Long projectId, Long id) {
        return selectOne(new QueryWrapper<StoryboardSubtitleEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("id", id)
            .eq("status", ShotResultStatus.ACTIVE.name()));
    }

    default List<StoryboardSubtitleEntity> selectByStoryboard(Long tenantId, Long projectId, Long storyboardId) {
        return selectList(new QueryWrapper<StoryboardSubtitleEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("storyboard_id", storyboardId)
            .eq("status", ShotResultStatus.ACTIVE.name())
            .orderByDesc("id"));
    }
}

@Mapper
interface ShotComposeTaskMapper extends BaseMapper<ShotComposeTaskEntity> {
    default ShotComposeTaskEntity selectActive(Long tenantId, Long projectId, Long id) {
        return selectOne(new QueryWrapper<ShotComposeTaskEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("id", id)
            .isNull("deleted_at"));
    }

    default List<ShotComposeTaskEntity> selectByProject(Long tenantId, Long projectId, String status, Long storyboardId) {
        QueryWrapper<ShotComposeTaskEntity> query = new QueryWrapper<ShotComposeTaskEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .isNull("deleted_at")
            .orderByDesc("id");
        if (status != null && !status.isBlank()) {
            query.eq("status", status);
        }
        if (storyboardId != null) {
            query.eq("storyboard_id", storyboardId);
        }
        return selectList(query);
    }
}

@Mapper
interface ShotComposeResultMapper extends BaseMapper<ShotComposeResultEntity> {
    default ShotComposeResultEntity selectActive(Long tenantId, Long projectId, Long id) {
        return selectOne(new QueryWrapper<ShotComposeResultEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("id", id)
            .eq("status", ShotResultStatus.ACTIVE.name()));
    }

    default List<ShotComposeResultEntity> selectByTask(Long tenantId, Long projectId, Long taskId) {
        return selectList(new QueryWrapper<ShotComposeResultEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("task_id", taskId)
            .eq("status", ShotResultStatus.ACTIVE.name())
            .orderByAsc("id"));
    }
}
