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

@Mapper
interface EpisodeComposeTaskMapper extends BaseMapper<EpisodeComposeTaskEntity> {
    default EpisodeComposeTaskEntity selectActive(Long tenantId, Long projectId, Long id) {
        return selectOne(new QueryWrapper<EpisodeComposeTaskEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("id", id)
            .isNull("deleted_at"));
    }

    default List<EpisodeComposeTaskEntity> selectByProject(Long tenantId, Long projectId, Integer episodeNo, String status) {
        QueryWrapper<EpisodeComposeTaskEntity> query = new QueryWrapper<EpisodeComposeTaskEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .isNull("deleted_at")
            .orderByDesc("id");
        if (episodeNo != null) {
            query.eq("episode_no", episodeNo);
        }
        if (status != null && !status.isBlank()) {
            query.eq("status", status);
        }
        return selectList(query);
    }
}

@Mapper
interface EpisodeComposeItemMapper extends BaseMapper<EpisodeComposeItemEntity> {
    default List<EpisodeComposeItemEntity> selectByTask(Long tenantId, Long projectId, Long taskId) {
        return selectList(new QueryWrapper<EpisodeComposeItemEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("task_id", taskId)
            .orderByAsc("storyboard_order"));
    }
}

@Mapper
interface EpisodeVideoVersionMapper extends BaseMapper<EpisodeVideoVersionEntity> {
    default EpisodeVideoVersionEntity selectActive(Long tenantId, Long projectId, Long id) {
        return selectOne(new QueryWrapper<EpisodeVideoVersionEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("id", id)
            .eq("status", ShotResultStatus.ACTIVE.name()));
    }

    default EpisodeVideoVersionEntity selectByTask(Long tenantId, Long projectId, Long taskId) {
        return selectOne(new QueryWrapper<EpisodeVideoVersionEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("compose_task_id", taskId)
            .eq("status", ShotResultStatus.ACTIVE.name())
            .last("limit 1"));
    }

    default List<EpisodeVideoVersionEntity> selectByEpisode(Long tenantId, Long projectId, Integer episodeNo) {
        return selectList(new QueryWrapper<EpisodeVideoVersionEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("episode_no", episodeNo)
            .eq("status", ShotResultStatus.ACTIVE.name())
            .orderByDesc("is_current")
            .orderByDesc("version_no"));
    }
}

@Mapper
interface EpisodeExportRecordMapper extends BaseMapper<EpisodeExportRecordEntity> {
    default List<EpisodeExportRecordEntity> selectByProject(Long tenantId, Long projectId, Integer episodeNo) {
        QueryWrapper<EpisodeExportRecordEntity> query = new QueryWrapper<EpisodeExportRecordEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .orderByDesc("id");
        if (episodeNo != null) {
            query.eq("episode_no", episodeNo);
        }
        return selectList(query);
    }
}
