package com.antshorttv.drama;

import com.antshorttv.drama.DramaModels.*;
import com.antshorttv.shot.EpisodeVideoVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper interface DramaMapper extends BaseMapper<Drama> {
    @Select("select * from library_drama where tenant_id=#{tenantId} and id=#{id} and deleted_at is null for update")
    Drama lock(Long tenantId, Long id);
}
@Mapper interface LibraryEpisodeMapper extends BaseMapper<Episode> {}
@Mapper interface MiniappMapper extends BaseMapper<Miniapp> {
    @Select("select * from team_miniapp where tenant_id=#{tenantId} and id=#{id} for update")
    Miniapp lock(Long tenantId, Long id);
}
@Mapper interface ReleaseMapper extends BaseMapper<Release> {}
@Mapper interface ReleaseEpisodeMapper extends BaseMapper<ReleaseEpisode> {}
@Mapper interface ViewerSessionMapper extends BaseMapper<ViewerSession> {}
@Mapper interface DramaSourceMapper {
    @Select("select * from episode_video_version where tenant_id=#{tenantId} and project_id=#{projectId} and id=#{id} and status='ACTIVE' for update")
    EpisodeVideoVersionEntity lock(Long tenantId, Long projectId, Long id);
}
