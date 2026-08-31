package com.antshorttv.script;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
interface ScriptEpisodeMapper extends BaseMapper<ScriptEpisodeEntity> {}

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
