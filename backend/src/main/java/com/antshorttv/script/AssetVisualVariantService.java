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
public class AssetVisualVariantService {
    private final AssetVisualVariantMapper variantMapper;
    private final JdbcTemplate jdbc;

    public AssetVisualVariantService(AssetVisualVariantMapper variantMapper, JdbcTemplate jdbc) {
        this.variantMapper = variantMapper;
        this.jdbc = jdbc;
    }

    public List<VariantResponse> list(Long tenantId, Long projectId, String assetType, Long assetId) {
        AssetType type = AssetType.fromStorageValue(assetType);
        requireAsset(tenantId, projectId, type, assetId);
        return variantMapper.selectList(new QueryWrapper<AssetVisualVariantEntity>()
                .eq("tenant_id", tenantId).eq("project_id", projectId)
                .eq("asset_type", type.name()).eq("asset_id", assetId)
                .isNull("deleted_at").orderByDesc("is_primary").orderByAsc("id"))
            .stream().map(this::response).toList();
    }

    @Transactional
    public VariantResponse create(
        Long tenantId, Long projectId, String assetType, Long assetId, Long userId, VariantCommand command
    ) {
        AssetType type = AssetType.fromStorageValue(assetType);
        requireAsset(tenantId, projectId, type, assetId);
        if (command == null || command.name() == null || command.name().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "视觉形象名称不能为空。");
        }
        long existing = variantMapper.selectCount(new QueryWrapper<AssetVisualVariantEntity>()
            .eq("tenant_id", tenantId).eq("project_id", projectId).eq("asset_type", type.name())
            .eq("asset_id", assetId).isNull("deleted_at"));
        boolean primary = Boolean.TRUE.equals(command.primary()) || existing == 0;
        if (primary) {
            clearPrimary(tenantId, projectId, type, assetId);
        }
        AssetVisualVariantEntity entity = new AssetVisualVariantEntity();
        entity.setTenantId(tenantId);
        entity.setProjectId(projectId);
        entity.setAssetType(type.name());
        entity.setAssetId(assetId);
        entity.setName(command.name().trim());
        entity.setAppearance(blankToNull(command.appearance()));
        entity.setPrompt(blankToNull(command.prompt()));
        entity.setSourceType(defaultValue(command.sourceType(), "MANUAL"));
        entity.setGenerationStatus(defaultValue(command.generationStatus(), "NOT_STARTED"));
        entity.setCurrentImageResultId(command.currentImageResultId());
        entity.setCurrentImageUrl(blankToNull(command.currentImageUrl()));
        entity.setIsPrimary(primary);
        entity.setCreatedBy(userId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        variantMapper.insert(entity);
        if (primary) publishLegacyIfUsable(entity);
        return response(entity);
    }

    @Transactional
    public VariantResponse update(
        Long tenantId, Long projectId, Long variantId, VariantCommand command
    ) {
        AssetVisualVariantEntity entity = requireVariant(tenantId, projectId, variantId, true);
        if (command == null || command.name() == null || command.name().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "视觉形象名称不能为空。");
        }
        entity.setName(command.name().trim());
        entity.setAppearance(blankToNull(command.appearance()));
        entity.setPrompt(blankToNull(command.prompt()));
        entity.setUpdatedAt(LocalDateTime.now());
        variantMapper.updateById(entity);
        if (Boolean.TRUE.equals(command.primary()) && !Boolean.TRUE.equals(entity.getIsPrimary())) {
            return selectPrimary(tenantId, projectId, variantId);
        }
        return response(entity);
    }

    @Transactional
    public VariantResponse selectPrimary(Long tenantId, Long projectId, Long variantId) {
        AssetVisualVariantEntity entity = requireVariant(tenantId, projectId, variantId, true);
        AssetType type = AssetType.fromStorageValue(entity.getAssetType());
        clearPrimary(tenantId, projectId, type, entity.getAssetId());
        entity.setIsPrimary(true);
        entity.setUpdatedAt(LocalDateTime.now());
        variantMapper.updateById(entity);
        publishLegacyIfUsable(entity);
        return response(entity);
    }

    @Transactional
    public void delete(Long tenantId, Long projectId, Long variantId) {
        AssetVisualVariantEntity entity = requireVariant(tenantId, projectId, variantId, true);
        boolean wasPrimary = Boolean.TRUE.equals(entity.getIsPrimary());
        entity.setIsPrimary(false);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        variantMapper.updateById(entity);
        jdbc.update("""
            update asset_visual_variant_episode
               set binding_status = 'RETIRED', is_preferred = false, retired_at = now(), updated_at = now()
             where tenant_id = ? and project_id = ? and variant_id = ? and retired_at is null
            """, tenantId, projectId, variantId);
        if (wasPrimary) {
            AssetVisualVariantEntity replacement = variantMapper.selectOne(
                new QueryWrapper<AssetVisualVariantEntity>()
                    .eq("tenant_id", tenantId).eq("project_id", projectId)
                    .eq("asset_type", entity.getAssetType()).eq("asset_id", entity.getAssetId())
                    .isNull("deleted_at")
                    .orderByDesc("case when generation_status = 'COMPLETED' and current_image_url is not null then 1 else 0 end")
                    .orderByAsc("id").last("limit 1")
            );
            if (replacement != null) {
                replacement.setIsPrimary(true);
                replacement.setUpdatedAt(LocalDateTime.now());
                variantMapper.updateById(replacement);
            }
            if (replacement != null && usable(replacement)) {
                publishLegacyIfUsable(replacement);
            } else {
                clearLegacyImage(entity);
            }
        } else if (legacyReferencesVariant(entity)) {
            AssetVisualVariantEntity fallback = findUsableFallback(entity);
            if (fallback == null) {
                clearLegacyImage(entity);
            } else {
                publishLegacyFallback(fallback);
            }
        }
    }

    @Transactional
    public VariantResponse generationStarted(
        Long tenantId, Long projectId, Long variantId, Long generationTaskId
    ) {
        replaceGenerationOwner(tenantId, projectId, variantId, generationTaskId);
        return response(requireVariant(tenantId, projectId, variantId, true));
    }

    @Transactional
    public Long replaceGenerationOwner(
        Long tenantId, Long projectId, Long variantId, Long generationTaskId
    ) {
        java.util.List<Long> previousOwners = jdbc.query("""
            select generation_task_id from asset_visual_variant
             where id = ? and tenant_id = ? and project_id = ? and deleted_at is null
             for update
            """, (rs, rowNum) -> rs.getObject(1, Long.class), variantId, tenantId, projectId);
        if (previousOwners.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "视觉形象不存在。");
        }
        Long previousTaskId = previousOwners.get(0);
        AssetVisualVariantEntity entity = requireVariant(tenantId, projectId, variantId, true);
        entity.setGenerationTaskId(generationTaskId);
        entity.setGenerationStatus("GENERATING");
        entity.setGenerationErrorCode(null);
        entity.setGenerationErrorMessage(null);
        entity.setUpdatedAt(LocalDateTime.now());
        variantMapper.updateById(entity);
        return previousTaskId;
    }

    @Transactional
    public VariantResponse generationSucceeded(
        Long tenantId, Long projectId, Long variantId, Long imageResultId, String imageUrl
    ) {
        AssetVisualVariantEntity entity = requireVariant(tenantId, projectId, variantId, true);
        entity.setGenerationStatus("COMPLETED");
        entity.setCurrentImageResultId(imageResultId);
        entity.setCurrentImageUrl(blankToNull(imageUrl));
        entity.setGenerationErrorCode(null);
        entity.setGenerationErrorMessage(null);
        entity.setUpdatedAt(LocalDateTime.now());
        variantMapper.updateById(entity);
        if (Boolean.TRUE.equals(entity.getIsPrimary())) publishLegacyIfUsable(entity);
        return response(entity);
    }

    @Transactional
    public boolean generationSucceededIfClaimActive(
        Long tenantId, Long projectId, Long variantId, Long generationTaskId,
        Long imageResultId, String imageUrl, Long executionId, String claimToken
    ) {
        requireVariant(tenantId, projectId, variantId, true);
        int updated = jdbc.update("""
            update asset_visual_variant
               set generation_status = 'COMPLETED', current_image_result_id = ?, current_image_url = ?,
                   generation_error_code = null, generation_error_message = null, updated_at = now()
             where id = ? and tenant_id = ? and project_id = ? and deleted_at is null
               and generation_task_id = ?
               and exists (select 1 from ai_execution_task e
                 where e.id = ? and e.status = 'RUNNING' and e.claim_token = ?)
            """, imageResultId, blankToNull(imageUrl), variantId, tenantId, projectId,
            generationTaskId, executionId, claimToken);
        if (updated == 1) {
            AssetVisualVariantEntity published = variantMapper.selectById(variantId);
            if (published != null && Boolean.TRUE.equals(published.getIsPrimary())) publishLegacyIfUsable(published);
        }
        return updated == 1;
    }

    @Transactional
    public void discardGeneratedResult(Long tenantId, Long projectId, Long variantId, Long imageResultId) {
        int updated = jdbc.update("""
            update asset_visual_variant
               set current_image_result_id = null, current_image_url = null,
                   generation_status = case when generation_status = 'COMPLETED' then 'NOT_STARTED' else generation_status end,
                   updated_at = now()
             where id = ? and tenant_id = ? and project_id = ? and current_image_result_id = ?
            """, variantId, tenantId, projectId, imageResultId);
        if (updated == 1) {
            AssetVisualVariantEntity variant = variantMapper.selectById(variantId);
            if (variant != null && Boolean.TRUE.equals(variant.getIsPrimary())) {
                clearLegacyImageIfResultMatches(variant, imageResultId);
            }
        }
    }

    @Transactional
    public VariantResponse generationFailed(
        Long tenantId, Long projectId, Long variantId, String errorCode, String errorMessage
    ) {
        AssetVisualVariantEntity entity = requireVariant(tenantId, projectId, variantId, true);
        entity.setGenerationStatus("FAILED");
        entity.setGenerationErrorCode(blankToNull(errorCode));
        entity.setGenerationErrorMessage(blankToNull(errorMessage));
        entity.setUpdatedAt(LocalDateTime.now());
        variantMapper.updateById(entity);
        return response(entity);
    }

    @Transactional
    public boolean generationFailedIfClaimActive(
        Long tenantId, Long projectId, Long variantId, Long generationTaskId,
        String errorCode, String errorMessage, Long executionId, String claimToken
    ) {
        requireVariant(tenantId, projectId, variantId, true);
        return jdbc.update("""
            update asset_visual_variant
               set generation_status = 'FAILED', generation_error_code = ?, generation_error_message = ?,
                   updated_at = now()
             where id = ? and tenant_id = ? and project_id = ? and deleted_at is null
               and generation_task_id = ?
               and exists (select 1 from ai_execution_task e
                 where e.id = ? and e.status = 'RUNNING' and e.claim_token = ?)
            """, blankToNull(errorCode), blankToNull(errorMessage), variantId, tenantId, projectId,
            generationTaskId, executionId, claimToken) == 1;
    }

    public ResolvedVisual primaryVisual(Long tenantId, Long projectId, String assetType, Long assetId) {
        AssetType type = AssetType.fromStorageValue(assetType);
        requireAsset(tenantId, projectId, type, assetId);
        AssetVisualVariantEntity primary = variantMapper.selectOne(new QueryWrapper<AssetVisualVariantEntity>()
            .eq("tenant_id", tenantId).eq("project_id", projectId).eq("asset_type", type.name())
            .eq("asset_id", assetId).eq("is_primary", true).isNull("deleted_at").last("limit 1"));
        if (primary == null || !usable(primary)) {
            return null;
        }
        return new ResolvedVisual(primary.getId(), primary.getCurrentImageResultId(),
            primary.getCurrentImageUrl(), "PRIMARY_VARIANT");
    }

    private void clearPrimary(Long tenantId, Long projectId, AssetType type, Long assetId) {
        jdbc.update("""
            update asset_visual_variant set is_primary = false, updated_at = now()
             where tenant_id = ? and project_id = ? and asset_type = ? and asset_id = ?
               and is_primary = true and deleted_at is null
            """, tenantId, projectId, type.name(), assetId);
    }

    private void publishLegacyIfUsable(AssetVisualVariantEntity primary) {
        if (primary == null || primary.getDeletedAt() != null
            || !Boolean.TRUE.equals(primary.getIsPrimary()) || !usable(primary)) {
            return;
        }
        publishLegacyFallback(primary);
    }

    private void publishLegacyFallback(AssetVisualVariantEntity variant) {
        AssetType type = AssetType.fromStorageValue(variant.getAssetType());
        String table = switch (type) {
            case CHARACTER -> "character_asset";
            case SCENE -> "scene_asset";
            case PROP -> "prop_asset";
        };
        jdbc.update("update " + table + " set main_image_result_id = ?, main_image_url = ?, updated_at = now()"
                + " where tenant_id = ? and project_id = ? and id = ? and deleted_at is null",
            variant.getCurrentImageResultId(), variant.getCurrentImageUrl(),
            variant.getTenantId(), variant.getProjectId(), variant.getAssetId());
    }

    private boolean legacyReferencesVariant(AssetVisualVariantEntity variant) {
        AssetType type = AssetType.fromStorageValue(variant.getAssetType());
        String table = switch (type) {
            case CHARACTER -> "character_asset";
            case SCENE -> "scene_asset";
            case PROP -> "prop_asset";
        };
        Integer count = jdbc.queryForObject("select count(*) from " + table
                + " where tenant_id = ? and project_id = ? and id = ? and deleted_at is null"
                + " and ((main_image_result_id is not null and main_image_result_id = ?)"
                + " or (main_image_result_id is null and main_image_url = ?))",
            Integer.class, variant.getTenantId(), variant.getProjectId(), variant.getAssetId(),
            variant.getCurrentImageResultId(), variant.getCurrentImageUrl());
        return count != null && count > 0;
    }

    private AssetVisualVariantEntity findUsableFallback(AssetVisualVariantEntity deleted) {
        return variantMapper.selectOne(new QueryWrapper<AssetVisualVariantEntity>()
            .eq("tenant_id", deleted.getTenantId()).eq("project_id", deleted.getProjectId())
            .eq("asset_type", deleted.getAssetType()).eq("asset_id", deleted.getAssetId())
            .eq("generation_status", "COMPLETED").isNull("deleted_at")
            .and(query -> query.isNotNull("current_image_result_id").or().isNotNull("current_image_url"))
            .orderByDesc("is_primary").orderByAsc("id").last("limit 1"));
    }

    private void clearLegacyImage(AssetVisualVariantEntity variant) {
        updateLegacyImage(variant, null, null, null);
    }

    private void clearLegacyImageIfResultMatches(AssetVisualVariantEntity variant, Long imageResultId) {
        updateLegacyImage(variant, null, null, imageResultId);
    }

    private void updateLegacyImage(
        AssetVisualVariantEntity variant, Long imageResultId, String imageUrl, Long expectedCurrentResultId
    ) {
        AssetType type = AssetType.fromStorageValue(variant.getAssetType());
        String table = switch (type) {
            case CHARACTER -> "character_asset";
            case SCENE -> "scene_asset";
            case PROP -> "prop_asset";
        };
        String expected = expectedCurrentResultId == null ? "" : " and main_image_result_id = ?";
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(imageResultId);
        args.add(imageUrl);
        args.add(variant.getTenantId());
        args.add(variant.getProjectId());
        args.add(variant.getAssetId());
        if (expectedCurrentResultId != null) args.add(expectedCurrentResultId);
        jdbc.update("update " + table + " set main_image_result_id = ?, main_image_url = ?, updated_at = now()"
            + " where tenant_id = ? and project_id = ? and id = ? and deleted_at is null" + expected,
            args.toArray());
    }

    private void requireAsset(Long tenantId, Long projectId, AssetType type, Long assetId) {
        String table = switch (type) {
            case CHARACTER -> "character_asset";
            case SCENE -> "scene_asset";
            case PROP -> "prop_asset";
        };
        Integer count = jdbc.queryForObject("select count(*) from " + table
            + " where tenant_id = ? and project_id = ? and id = ? and deleted_at is null",
            Integer.class, tenantId, projectId, assetId);
        if (count == null || count != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "逻辑资产不存在或不属于当前项目。");
        }
    }

    private AssetVisualVariantEntity requireVariant(
        Long tenantId, Long projectId, Long variantId, boolean active
    ) {
        QueryWrapper<AssetVisualVariantEntity> query = new QueryWrapper<AssetVisualVariantEntity>()
            .eq("tenant_id", tenantId).eq("project_id", projectId).eq("id", variantId);
        if (active) query.isNull("deleted_at");
        AssetVisualVariantEntity entity = variantMapper.selectOne(query.last("limit 1"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "视觉形象不存在。");
        }
        requireAsset(tenantId, projectId, AssetType.fromStorageValue(entity.getAssetType()), entity.getAssetId());
        return entity;
    }

    private boolean usable(AssetVisualVariantEntity entity) {
        return "COMPLETED".equals(entity.getGenerationStatus())
            && (entity.getCurrentImageResultId() != null
                || (entity.getCurrentImageUrl() != null && !entity.getCurrentImageUrl().isBlank()));
    }

    private VariantResponse response(AssetVisualVariantEntity entity) {
        return new VariantResponse(entity.getId(), entity.getAssetType(), entity.getAssetId(), entity.getName(),
            entity.getAppearance(), entity.getPrompt(), entity.getSourceType(), entity.getGenerationStatus(),
            entity.getGenerationTaskId(), entity.getCurrentImageResultId(), entity.getCurrentImageUrl(),
            entity.getGenerationErrorCode(), entity.getGenerationErrorMessage(),
            Boolean.TRUE.equals(entity.getIsPrimary()), usable(entity));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
    }

    public record VariantCommand(
        String name, String appearance, String prompt, String sourceType, String generationStatus,
        Long currentImageResultId, String currentImageUrl, Boolean primary
    ) {}
    public record VariantResponse(
        Long id, String assetType, Long assetId, String name, String appearance, String prompt,
        String sourceType, String generationStatus, Long generationTaskId, Long currentImageResultId,
        String currentImageUrl, String errorCode, String errorMessage, boolean primary, boolean usable
    ) {}
    public record ResolvedVisual(Long variantId, Long imageResultId, String imageUrl, String source) {}
}
