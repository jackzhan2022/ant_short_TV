package com.antshorttv.script;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StoryboardMapper extends BaseMapper<StoryboardEntity> {
    default StoryboardEntity selectActive(Long tenantId, Long projectId, Long id) {
        return selectOne(new QueryWrapper<StoryboardEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("id", id)
            .isNull("deleted_at"));
    }

    default List<StoryboardEntity> selectByProject(Long tenantId, Long projectId) {
        return selectList(new QueryWrapper<StoryboardEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .isNull("deleted_at")
            .orderByAsc("episode_no")
            .orderByAsc("storyboard_no")
            .orderByAsc("shot_no"));
    }


    default List<StoryboardEntity> selectActiveEpisode(Long tenantId, Long projectId, Long episodeId) {
        return selectList(new QueryWrapper<StoryboardEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("episode_id", episodeId)
            .isNull("deleted_at")
            .orderByAsc("storyboard_no")
            .orderByAsc("id"));
    }
}
