package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryboardAssetReferenceService {
    private final ProjectPermissionGuard permissionGuard;
    private final JdbcTemplate jdbc;
    private final StoryboardAssetReferenceRepository repository;

    public StoryboardAssetReferenceService(
        ProjectPermissionGuard permissionGuard,
        JdbcTemplate jdbc,
        StoryboardAssetReferenceRepository repository
    ) {
        this.permissionGuard = permissionGuard;
        this.jdbc = jdbc;
        this.repository = repository;
    }

    @Transactional
    public List<StoryboardAssetReferenceResponse> replace(
        Long tenantId,
        Long projectId,
        Long storyboardId,
        ReplaceStoryboardAssetReferencesRequest request
    ) {
        TenantContext context = permissionGuard.require(tenantId, projectId, "STORYBOARD:EDIT");
        StoryboardScope storyboard = requireStoryboard(tenantId, projectId, storyboardId);
        List<StoryboardAssetReferenceCommand> commands = request == null || request.references() == null
            ? List.of() : request.references();
        validateOrder(commands);
        List<ResolvedReference> resolved = new ArrayList<>();
        for (StoryboardAssetReferenceCommand command : commands) {
            resolved.add(resolve(tenantId, projectId, storyboard.episodeId(), command));
        }
        LocalDateTime now = LocalDateTime.now();
        List<StoryboardAssetReferenceEntity> entities = new ArrayList<>();
        for (ResolvedReference item : resolved) {
            StoryboardAssetReferenceEntity entity = new StoryboardAssetReferenceEntity();
            entity.assetType = item.assetType().name();
            entity.assetId = item.assetId();
            entity.variantId = item.variant() == null ? null : item.variant().id();
            entity.referenceRole = item.role().name();
            entity.sortOrder = item.sortOrder();
            entity.resolutionStatus = item.status().name();
            entity.sourceType = StoryboardReferenceSourceType.MANUAL.name();
            entity.sourceName = item.sourceName();
            entity.lockedByUser = true;
            entity.createdBy = context.userId();
            entities.add(entity);
        }
        repository.replace(tenantId, projectId, storyboardId, entities, now);
        synchronizeLegacyFields(tenantId, projectId, storyboardId, resolved);
        return repository.listActive(tenantId, projectId, storyboardId).stream()
            .map(entity -> responseForPersisted(tenantId, projectId, entity))
            .toList();
    }

    public List<StoryboardAssetReferenceResponse> list(
        Long tenantId, Long projectId, Long storyboardId
    ) {
        permissionGuard.require(tenantId, projectId, "STORYBOARD:VIEW");
        requireStoryboard(tenantId, projectId, storyboardId);
        return repository.listActive(tenantId, projectId, storyboardId).stream()
            .map(entity -> responseForPersisted(tenantId, projectId, entity))
            .toList();
    }

    private StoryboardScope requireStoryboard(Long tenantId, Long projectId, Long storyboardId) {
        List<StoryboardScope> rows = jdbc.query("""
            select id, episode_id from storyboard
             where id = ? and tenant_id = ? and project_id = ? and deleted_at is null
             limit 1
            """, (rs, rowNum) -> new StoryboardScope(
                rs.getLong("id"), rs.getObject("episode_id", Long.class)),
            storyboardId, tenantId, projectId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "分镜不存在。");
        return rows.get(0);
    }

    private void validateOrder(List<StoryboardAssetReferenceCommand> commands) {
        Set<String> positions = new HashSet<>();
        for (StoryboardAssetReferenceCommand command : commands) {
            StoryboardReferenceAssetType type = StoryboardReferenceAssetType.parse(command.assetType());
            StoryboardReferenceRole.parse(command.referenceRole());
            if (command.assetId() == null) {
                throw StoryboardAssetReferenceValidation.invalid("手工素材引用必须选择当前项目资产。");
            }
            if (command.sortOrder() == null || command.sortOrder() < 0) {
                throw StoryboardAssetReferenceValidation.invalid("素材排序必须为非负整数。");
            }
            if (!positions.add(type.name() + ":" + command.sortOrder())) {
                throw StoryboardAssetReferenceValidation.invalid("同类素材排序不能重复。");
            }
        }
    }

    private ResolvedReference resolve(
        Long tenantId,
        Long projectId,
        Long episodeId,
        StoryboardAssetReferenceCommand command
    ) {
        StoryboardReferenceAssetType type = StoryboardReferenceAssetType.parse(command.assetType());
        StoryboardReferenceRole role = StoryboardReferenceRole.parse(command.referenceRole());
        AssetRef asset = requireAsset(tenantId, projectId, type, command.assetId());
        VariantRef variant = command.variantId() == null
            ? chooseVariant(tenantId, projectId, episodeId, type, command.assetId())
            : requireVariant(tenantId, projectId, type, command.assetId(), command.variantId(), true);
        StoryboardReferenceResolutionStatus status = variant != null && variant.usable()
            ? StoryboardReferenceResolutionStatus.RESOLVED
            : StoryboardReferenceResolutionStatus.ASSET_PENDING;
        String sourceName = command.sourceName() == null || command.sourceName().isBlank()
            ? asset.name() : command.sourceName().trim();
        return new ResolvedReference(
            type, asset.id(), asset.name(), variant, role, command.sortOrder(), status, sourceName);
    }

    private AssetRef requireAsset(
        Long tenantId, Long projectId, StoryboardReferenceAssetType type, Long assetId
    ) {
        String table = switch (type) {
            case CHARACTER -> "character_asset";
            case SCENE -> "scene_asset";
            case PROP -> "prop_asset";
        };
        List<AssetRef> rows = jdbc.query("select id,name from " + table
                + " where id=? and tenant_id=? and project_id=? and deleted_at is null limit 1",
            (rs, rowNum) -> new AssetRef(rs.getLong("id"), rs.getString("name")),
            assetId, tenantId, projectId);
        if (rows.isEmpty()) {
            throw StoryboardAssetReferenceValidation.invalid("只能引用当前项目中的有效素材。");
        }
        return rows.get(0);
    }

    private VariantRef requireVariant(
        Long tenantId,
        Long projectId,
        StoryboardReferenceAssetType type,
        Long assetId,
        Long variantId,
        boolean lock
    ) {
        return variants(tenantId, projectId, type, assetId, lock).stream()
            .filter(item -> item.id().equals(variantId))
            .findFirst()
            .orElseThrow(() -> StoryboardAssetReferenceValidation.invalid("视觉形象不属于所选素材。"));
    }

    private VariantRef chooseVariant(
        Long tenantId,
        Long projectId,
        Long episodeId,
        StoryboardReferenceAssetType type,
        Long assetId
    ) {
        List<VariantRef> variants = variants(tenantId, projectId, type, assetId, true);
        if (variants.isEmpty()) return null;
        if (episodeId != null) {
            List<Long> preferred = jdbc.queryForList("""
                select variant_id from asset_visual_variant_episode
                 where tenant_id=? and project_id=? and episode_id=? and asset_type=? and asset_id=?
                   and is_preferred=true and binding_status='ACTIVE' and retired_at is null
                 order by id limit 1
                """, Long.class, tenantId, projectId, episodeId, type.name(), assetId);
            if (!preferred.isEmpty()) {
                VariantRef matched = variants.stream()
                    .filter(item -> item.id().equals(preferred.get(0))).findFirst().orElse(null);
                if (matched != null) return matched;
            }
        }
        VariantRef usablePrimary = variants.stream()
            .filter(item -> item.primary() && item.usable()).findFirst().orElse(null);
        if (usablePrimary != null) return usablePrimary;
        List<VariantRef> usable = variants.stream().filter(VariantRef::usable).toList();
        if (usable.size() == 1) return usable.get(0);
        VariantRef primary = variants.stream().filter(VariantRef::primary).findFirst().orElse(null);
        if (primary != null) return primary;
        return variants.size() == 1 ? variants.get(0) : null;
    }

    private List<VariantRef> variants(
        Long tenantId, Long projectId, StoryboardReferenceAssetType type, Long assetId, boolean lock
    ) {
        return jdbc.query("""
            select id,name,generation_status,current_image_result_id,current_image_url,is_primary
              from asset_visual_variant
             where tenant_id=? and project_id=? and asset_type=? and asset_id=? and deleted_at is null
             order by is_primary desc,id
            """ + (lock ? " for update" : ""), (rs, rowNum) -> {
                String url = rs.getString("current_image_url");
                boolean usable = "COMPLETED".equals(rs.getString("generation_status"))
                    && (rs.getObject("current_image_result_id") != null || (url != null && !url.isBlank()));
                return new VariantRef(
                    rs.getLong("id"), rs.getString("name"), url, rs.getBoolean("is_primary"), usable);
            }, tenantId, projectId, type.name(), assetId);
    }

    private void synchronizeLegacyFields(
        Long tenantId, Long projectId, Long storyboardId, List<ResolvedReference> references
    ) {
        String characters = names(references, StoryboardReferenceAssetType.CHARACTER);
        String scenes = names(references, StoryboardReferenceAssetType.SCENE);
        String props = names(references, StoryboardReferenceAssetType.PROP);
        jdbc.update("""
            update storyboard set characters=?,scene=?,props=?,updated_at=now()
             where id=? and tenant_id=? and project_id=? and deleted_at is null
            """, characters, scenes, props, storyboardId, tenantId, projectId);
    }

    private String names(List<ResolvedReference> references, StoryboardReferenceAssetType type) {
        String value = references.stream().filter(item -> item.assetType() == type)
            .map(ResolvedReference::assetName).distinct()
            .collect(java.util.stream.Collectors.joining("、"));
        return value.isBlank() ? null : value;
    }

    private StoryboardAssetReferenceResponse responseForPersisted(
        Long tenantId, Long projectId, StoryboardAssetReferenceEntity entity
    ) {
        StoryboardReferenceAssetType type = StoryboardReferenceAssetType.parse(entity.assetType);
        AssetRef asset = entity.assetId == null ? null : requireAsset(tenantId, projectId, type, entity.assetId);
        VariantRef variant = entity.variantId == null ? null
            : requireVariant(tenantId, projectId, type, entity.assetId, entity.variantId, false);
        return new StoryboardAssetReferenceResponse(
            entity.id, entity.assetType, entity.assetId, asset == null ? null : asset.name(),
            entity.variantId, variant == null ? null : variant.name(),
            variant == null ? null : variant.imageUrl(), entity.referenceRole, entity.sortOrder,
            entity.resolutionStatus, entity.sourceType, entity.sourceName,
            Boolean.TRUE.equals(entity.lockedByUser));
    }

    private record StoryboardScope(Long id, Long episodeId) {
    }

    private record AssetRef(Long id, String name) {
    }

    private record VariantRef(Long id, String name, String imageUrl, boolean primary, boolean usable) {
    }

    private record ResolvedReference(
        StoryboardReferenceAssetType assetType,
        Long assetId,
        String assetName,
        VariantRef variant,
        StoryboardReferenceRole role,
        Integer sortOrder,
        StoryboardReferenceResolutionStatus status,
        String sourceName
    ) {
    }
}
