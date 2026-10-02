package com.antshorttv.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.antshorttv.common.PageBounds;

@Mapper
interface VideoDecompositionBatchMapper extends BaseMapper<VideoDecompositionBatchEntity> {
    String VISIBLE = """
        b.tenant_id = #{tenantId} and b.deleted_at is null
        and (#{projectId} is null or b.project_id = #{projectId})
        and (b.project_id is null or exists (select 1 from project p
          where p.id = b.project_id and p.tenant_id = b.tenant_id and p.deleted_at is null
            and (#{wide} = true or exists (
              select 1 from project_member pm
              join project_role pr on pr.id = pm.role_id and pr.project_id = p.id
                and pr.tenant_id = p.tenant_id and pr.status = 'ACTIVE'
              join project_role_permission rp on rp.role_id = pr.id and rp.project_id = p.id
                and rp.tenant_id = p.tenant_id
              join permission perm on perm.id = rp.permission_id and perm.code = 'PROJECT:VIEW'
              where pm.project_id = p.id and pm.tenant_id = p.tenant_id
                and pm.user_id = #{userId} and pm.status = 'ACTIVE'))))
        """;

    @Select("select count(*) from video_decomposition_batch b where " + VISIBLE)
    long countVisible(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
        @Param("wide") boolean wide, @Param("projectId") Long projectId);

    @Select("select b.* from video_decomposition_batch b where " + VISIBLE
        + " order by b.created_at desc, b.id desc limit #{bounds.pageSize} offset #{bounds.offset}")
    List<VideoDecompositionBatchEntity> selectVisiblePage(@Param("tenantId") Long tenantId,
        @Param("userId") Long userId, @Param("wide") boolean wide, @Param("projectId") Long projectId,
        @Param("bounds") PageBounds bounds);

    @Select("""
        <script>
        select ep.batch_id, count(*) as total,
          sum(case when ep.status in ('SUCCEEDED','ANALYSIS_SUCCEEDED','PENDING_REVIEW','CONFIRMED') then 1 else 0 end) as succeeded,
          sum(case when ep.status = 'FAILED' then 1 else 0 end) as failed,
          sum(case when ep.status in ('ANALYZING','DRAFT_GENERATING') then 1 else 0 end) as processing,
          sum(case
            when ep.status in ('SUCCEEDED','ANALYSIS_SUCCEEDED','PENDING_REVIEW','CONFIRMED') then 100
            when ep.status = 'FAILED' then least(99, greatest(0, coalesce(ex.progress, 0)))
            when ep.status in ('ANALYZING','DRAFT_GENERATING') then least(99, greatest(1, coalesce(ex.progress, 50)))
            else 0 end) as progress_sum
          from video_decomposition_episode ep left join ai_execution_task ex on ex.id = ep.execution_id
         where ep.tenant_id = #{tenantId} and ep.batch_id in
         <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
         group by ep.batch_id
        </script>
        """)
    List<VideoDecompositionBatchStatistics> selectStatistics(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);
    default List<VideoDecompositionBatchEntity> selectByTenant(Long tenantId) {
        return selectList(new LambdaQueryWrapper<VideoDecompositionBatchEntity>()
            .eq(VideoDecompositionBatchEntity::getTenantId, tenantId)
            .isNull(VideoDecompositionBatchEntity::getDeletedAt)
            .orderByDesc(VideoDecompositionBatchEntity::getCreatedAt));
    }
}

@Mapper
interface VideoDecompositionEpisodeMapper extends BaseMapper<VideoDecompositionEpisodeEntity> {
    default List<VideoDecompositionEpisodeEntity> selectByBatch(Long tenantId, Long batchId) {
        return selectList(new LambdaQueryWrapper<VideoDecompositionEpisodeEntity>()
            .eq(VideoDecompositionEpisodeEntity::getTenantId, tenantId)
            .eq(VideoDecompositionEpisodeEntity::getBatchId, batchId)
            .orderByAsc(VideoDecompositionEpisodeEntity::getEpisodeNo));
    }
}

@Mapper
interface VideoDecompositionAnalysisMapper extends BaseMapper<VideoDecompositionAnalysisEntity> {
    default VideoDecompositionAnalysisEntity selectLatest(Long episodeId) {
        return selectOne(new LambdaQueryWrapper<VideoDecompositionAnalysisEntity>()
            .eq(VideoDecompositionAnalysisEntity::getEpisodeId, episodeId)
            .orderByDesc(VideoDecompositionAnalysisEntity::getCreatedAt)
            .last("limit 1"));
    }
}

@Mapper
interface VideoDecompositionAttemptMapper extends BaseMapper<VideoDecompositionAttemptEntity> {
}
