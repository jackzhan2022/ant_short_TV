package com.antshorttv.drama;

import com.antshorttv.drama.DramaModels.*;
import com.antshorttv.shot.EpisodeVideoVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

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
@Mapper interface WatchProgressMapper extends BaseMapper<WatchProgress> {
    @Insert("insert ignore into miniapp_watch_progress (miniapp_id,openid,drama_id,episode_id,position_seconds,duration_seconds,completed,client_sequence,playback_session,updated_at) values (#{appId},#{openid},#{dramaId},#{episodeId},0,0,false,-1,'',now())")
    int ensure(Long appId, String openid, Long dramaId, Long episodeId);

    @Select("select * from miniapp_watch_progress where miniapp_id=#{appId} and openid=#{openid} and drama_id=#{dramaId} for update")
    @Results({@Result(column="client_sequence", property="sequence")})
    WatchProgress lock(Long appId, String openid, Long dramaId);

    @Select("select w.*, r.title, r.cover_url, r.id as release_id, e.title as episode_title, e.episode_no from miniapp_watch_progress w join miniapp_drama_release r on r.miniapp_id=w.miniapp_id and r.drama_id=w.drama_id and r.published=true join miniapp_release_episode e on e.release_id=r.id and e.episode_id=w.episode_id where w.miniapp_id=#{appId} and w.openid=#{openid} order by w.updated_at desc, w.id desc limit 100")
    List<WatchProgress> history(Long appId, String openid);
}
@Mapper interface DramaSourceMapper {
    @Select("select * from episode_video_version where tenant_id=#{tenantId} and project_id=#{projectId} and id=#{id} and status='ACTIVE' for update")
    EpisodeVideoVersionEntity lock(Long tenantId, Long projectId, Long id);
}
