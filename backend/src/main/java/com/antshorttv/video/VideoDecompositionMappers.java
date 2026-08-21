package com.antshorttv.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
interface VideoDecompositionBatchMapper extends BaseMapper<VideoDecompositionBatchEntity> {
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
