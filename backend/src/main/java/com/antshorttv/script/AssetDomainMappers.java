package com.antshorttv.script;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
interface ScriptEpisodeMapper extends BaseMapper<ScriptEpisodeEntity> {
    default List<ScriptEpisodeEntity> selectNavigation(Long tenantId, Long projectId, Long scriptId) {
        return selectList(new QueryWrapper<ScriptEpisodeEntity>()
            .select("id", "episode_no", "title", "summary", "content_fingerprint", "generated_by_run_id")
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("script_id", scriptId)
            .isNull("retired_at")
            .orderByAsc("episode_no", "id"));
    }
}

@Mapper
interface ScriptEpisodeSummaryMapper extends BaseMapper<ScriptEpisodeSummaryEntity> {}

@Mapper
interface ScriptAssetNormalizationRunMapper extends BaseMapper<ScriptAssetNormalizationRunEntity> {}

@Mapper
interface ScriptAssetCandidateMapper extends BaseMapper<ScriptAssetCandidateEntity> {}

@Mapper
interface ScriptAssetCandidateAliasMapper extends BaseMapper<ScriptAssetCandidateAliasEntity> {}

@Mapper
interface ScriptAssetPromotionDecisionMapper extends BaseMapper<ScriptAssetPromotionDecisionEntity> {}

@Mapper
interface AssetVisualVariantMapper extends BaseMapper<AssetVisualVariantEntity> {}

@Mapper
interface AssetVisualVariantEpisodeMapper extends BaseMapper<AssetVisualVariantEpisodeEntity> {}
