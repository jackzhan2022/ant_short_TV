package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetVisualBindingService {
    private final AssetVisualVariantEpisodeMapper bindingMapper;
    private final AssetVisualVariantMapper variantMapper;
    private final ScriptEpisodeMapper episodeMapper;
    private final JdbcTemplate jdbc;

    public AssetVisualBindingService(
        AssetVisualVariantEpisodeMapper bindingMapper,
        AssetVisualVariantMapper variantMapper,
        ScriptEpisodeMapper episodeMapper,
        JdbcTemplate jdbc
    ) {
        this.bindingMapper = bindingMapper;
        this.variantMapper = variantMapper;
        this.episodeMapper = episodeMapper;
        this.jdbc = jdbc;
    }

    @Transactional
    public List<BindingResponse> bind(
        Long tenantId, Long projectId, Long variantId, Long userId, BindingCommand command
    ) {
        AssetVisualVariantEntity variant = variantMapper.selectOne(new QueryWrapper<AssetVisualVariantEntity>()
            .eq("tenant_id", tenantId).eq("project_id", projectId).eq("id", variantId)
            .isNull("deleted_at").last("limit 1"));
        if (variant == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "视觉形象不存在或不属于当前项目。");
        }
        lockAssetOwner(tenantId, projectId, variant.getAssetType(), variant.getAssetId());
        if (command == null || command.episodeIds() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "剧集列表不能为空。");
        }
        List<Long> selectedEpisodeIds = command.episodeIds().stream().distinct().toList();
        for (Long episodeId : selectedEpisodeIds) {
            ScriptEpisodeEntity episode = episodeMapper.selectOne(new QueryWrapper<ScriptEpisodeEntity>()
                .eq("tenant_id", tenantId).eq("project_id", projectId).eq("id", episodeId)
                .eq("status", "ACTIVE").isNull("retired_at").last("limit 1"));
            if (episode == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "剧集不存在、已退役或不属于当前项目。");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (AssetVisualVariantEpisodeEntity existing : bindingMapper.selectList(
            new QueryWrapper<AssetVisualVariantEpisodeEntity>()
                .eq("tenant_id", tenantId).eq("project_id", projectId).eq("variant_id", variantId)
                .isNull("retired_at"))) {
            if (!selectedEpisodeIds.contains(existing.getEpisodeId())) {
                existing.setIsPreferred(false);
                existing.setBindingStatus("RETIRED");
                existing.setRetiredAt(now);
                existing.setUpdatedAt(now);
                bindingMapper.updateById(existing);
            }
        }
        for (Long episodeId : selectedEpisodeIds) {
            ScriptEpisodeEntity episode = episodeMapper.selectOne(new QueryWrapper<ScriptEpisodeEntity>()
                .eq("tenant_id", tenantId).eq("project_id", projectId).eq("id", episodeId)
                .eq("status", "ACTIVE").isNull("retired_at").last("limit 1"));
            if (Boolean.TRUE.equals(command.preferred())) {
                jdbc.update("""
                    update asset_visual_variant_episode
                       set is_preferred = false, updated_at = now()
                     where tenant_id = ? and project_id = ? and episode_id = ?
                       and asset_type = ? and asset_id = ? and binding_status = 'ACTIVE'
                       and retired_at is null and is_preferred = true
                    """, tenantId, projectId, episodeId, variant.getAssetType(), variant.getAssetId());
            }
            AssetVisualVariantEpisodeEntity binding = bindingMapper.selectOne(
                new QueryWrapper<AssetVisualVariantEpisodeEntity>()
                    .eq("tenant_id", tenantId).eq("project_id", projectId)
                    .eq("variant_id", variantId).eq("episode_id", episodeId)
                    .isNull("retired_at").last("limit 1"));
            if (binding == null) {
                binding = new AssetVisualVariantEpisodeEntity();
                binding.setTenantId(tenantId);
                binding.setProjectId(projectId);
                binding.setScriptId(episode.getScriptId());
                binding.setEpisodeId(episodeId);
                binding.setAssetType(variant.getAssetType());
                binding.setAssetId(variant.getAssetId());
                binding.setVariantId(variantId);
                binding.setCreatedBy(userId);
                binding.setCreatedAt(LocalDateTime.now());
            }
            binding.setIsPreferred(Boolean.TRUE.equals(command.preferred()));
            binding.setBindingStatus("ACTIVE");
            binding.setRetiredAt(null);
            binding.setUpdatedAt(now);
            if (binding.getId() == null) bindingMapper.insert(binding); else bindingMapper.updateById(binding);
        }
        return list(tenantId, projectId, variant.getAssetType(), variant.getAssetId());
    }

    public List<BindingResponse> list(
        Long tenantId, Long projectId, String assetType, Long assetId
    ) {
        return jdbc.query("""
            select b.id, b.variant_id, b.episode_id, e.episode_no, e.title,
                   b.is_preferred, b.binding_status
              from asset_visual_variant_episode b
              join script_episode e on e.id = b.episode_id and e.tenant_id = b.tenant_id
             where b.tenant_id = ? and b.project_id = ? and b.asset_type = ? and b.asset_id = ?
               and b.retired_at is null
             order by e.episode_no, b.is_preferred desc, b.id
            """, (rs, rowNum) -> new BindingResponse(rs.getLong("id"), rs.getLong("variant_id"),
                rs.getLong("episode_id"), rs.getInt("episode_no"), rs.getString("title"),
                rs.getBoolean("is_preferred"), rs.getString("binding_status")),
            tenantId, projectId, AssetType.fromStorageValue(assetType).name(), assetId);
    }

    private void lockAssetOwner(Long tenantId, Long projectId, String assetType, Long assetId) {
        String table = switch (AssetType.fromStorageValue(assetType)) {
            case CHARACTER -> "character_asset";
            case SCENE -> "scene_asset";
            case PROP -> "prop_asset";
        };
        jdbc.queryForObject("select id from " + table
            + " where tenant_id = ? and project_id = ? and id = ? for update",
            Long.class, tenantId, projectId, assetId);
    }

    public record BindingCommand(List<Long> episodeIds, Boolean preferred) {}
    public record BindingResponse(
        Long id, Long variantId, Long episodeId, Integer episodeNo, String episodeTitle,
        boolean preferred, String status
    ) {}
}
