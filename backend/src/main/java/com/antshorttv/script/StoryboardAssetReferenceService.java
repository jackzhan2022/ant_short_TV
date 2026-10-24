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
    @org.springframework.beans.factory.annotation.Autowired private PropProductionService propProduction;
    private final ProjectPermissionGuard permissionGuard;
    private final JdbcTemplate jdbc;
    private final StoryboardAssetReferenceRepository repository;
    private final StoryboardAssetReferenceLegacyResolver legacyResolver;

    public StoryboardAssetReferenceService(
        ProjectPermissionGuard permissionGuard,
        JdbcTemplate jdbc,
        StoryboardAssetReferenceRepository repository,
        StoryboardAssetReferenceLegacyResolver legacyResolver
    ) {
        this.permissionGuard = permissionGuard;
        this.jdbc = jdbc;
        this.repository = repository;
        this.legacyResolver = legacyResolver;
    }

    @Transactional
    public List<StoryboardAssetReferenceResponse> replace(
        Long tenantId,
        Long projectId,
        Long storyboardId,
        ReplaceStoryboardAssetReferencesRequest request
    ) {
        TenantContext context = permissionGuard.require(tenantId, projectId, "STORYBOARD:EDIT");
        if(propProduction!=null) propProduction.lockProject(tenantId,projectId);
        StoryboardScope storyboard = requireStoryboard(tenantId, projectId, storyboardId);
        List<StoryboardAssetReferenceCommand> commands = request == null || request.references() == null
            ? List.of() : request.references();
        validateOrder(commands);
        List<LegacyReference> unresolved = new ArrayList<>();
        if (commands.stream().anyMatch(command -> command.assetId() == null)) {
            unresolved.addAll(legacyResolver.resolveStoryboard(tenantId, projectId, storyboardId)
                .references().stream()
                .filter(reference -> reference.assetId() == null
                    && "UNRESOLVED".equals(reference.resolutionStatus()))
                .toList());
        }
        List<ResolvedReference> resolved = new ArrayList<>();
        for (StoryboardAssetReferenceCommand command : commands) {
            resolved.add(command.assetId() == null ? retainUnresolved(command, unresolved)
                : resolve(tenantId, projectId, storyboard.episodeId(), command));
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
            entity.sourceType = item.sourceType();
            entity.sourceName = item.sourceName();
            entity.lockedByUser = item.lockedByUser();
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
            if (command.sortOrder() == null || command.sortOrder() < 0) {
                throw StoryboardAssetReferenceValidation.invalid("素材排序必须为非负整数。");
            }
            if (!positions.add(type.name() + ":" + command.sortOrder())) {
                throw StoryboardAssetReferenceValidation.invalid("同类素材排序不能重复。");
            }
        }
    }

    private ResolvedReference retainUnresolved(
        StoryboardAssetReferenceCommand command, List<LegacyReference> unresolved
    ) {
        StoryboardReferenceAssetType type = StoryboardReferenceAssetType.parse(command.assetType());
        StoryboardReferenceRole role = StoryboardReferenceRole.parse(command.referenceRole());
        if (command.variantId() == null && command.sourceName() != null && !command.sourceName().isBlank()) {
            for (int index = 0; index < unresolved.size(); index++) {
                LegacyReference existing = unresolved.get(index);
                if (type.name().equals(existing.assetType())
                    && command.sourceName().trim().equals(existing.sourceName())) {
                    // Consume each existing row once so replacement cannot introduce unbound references.
                    unresolved.remove(index);
                    return new ResolvedReference(type, null, null, null, role, command.sortOrder(),
                        StoryboardReferenceResolutionStatus.UNRESOLVED, existing.sourceName(),
                        existing.sourceType(), existing.lockedByUser());
                }
            }
        }
        throw StoryboardAssetReferenceValidation.invalid("手工素材引用必须选择当前项目资产。");
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
        if (type == StoryboardReferenceAssetType.PROP && propProduction != null
            && propProduction.target(tenantId,projectId,command.assetId(),null,episodeId).scene())
            status = StoryboardReferenceResolutionStatus.SCENE_INCLUDED;
        String sourceName = command.sourceName() == null || command.sourceName().isBlank()
            ? asset.name() : command.sourceName().trim();
        return new ResolvedReference(
            type, asset.id(), asset.name(), variant, role, command.sortOrder(), status, sourceName,
            StoryboardReferenceSourceType.MANUAL.name(), true);
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
        var own = variants(tenantId, projectId, type, assetId, lock).stream()
            .filter(item -> item.id().equals(variantId))
            .findFirst();
        if (own.isPresent()) return own.get();
        if (type == StoryboardReferenceAssetType.PROP && propProduction != null) {
            var target=propProduction.target(tenantId,projectId,assetId,variantId,null);
            if (!target.scene() && !target.unresolved() && target.variantId()!=null)
                return variants(tenantId,projectId,type,target.assetId(),lock).stream()
                    .filter(v -> v.id().equals(target.variantId())).findFirst()
                    .orElseThrow(() -> StoryboardAssetReferenceValidation.invalid("制作形态不存在。"));
        }
        throw StoryboardAssetReferenceValidation.invalid("视觉形象不属于所选素材或已确认的制作关系。");
    }

    private VariantRef chooseVariant(
        Long tenantId,
        Long projectId,
        Long episodeId,
        StoryboardReferenceAssetType type,
        Long assetId
    ) {
        if (type == StoryboardReferenceAssetType.PROP && propProduction != null && !propProduction.policy(tenantId,projectId,assetId).isEmpty()) {
            var target=propProduction.target(tenantId,projectId,assetId,null,episodeId);
            if(target.scene() || target.unresolved()) return null;
            if(target.variantId()!=null) return variants(tenantId,projectId,type,target.assetId(),true).stream()
                .filter(v -> v.id().equals(target.variantId())).findFirst().orElse(null);
            assetId=target.assetId();
        }
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
            .map(item -> item.assetName() == null ? item.sourceName() : item.assetName()).distinct()
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
        var response = new StoryboardAssetReferenceResponse(
            entity.id, entity.assetType, entity.assetId, asset == null ? null : asset.name(),
            entity.variantId, variant == null ? null : variant.name(),
            variant == null ? null : variant.imageUrl(), entity.referenceRole, entity.sortOrder,
            entity.resolutionStatus, entity.sourceType, entity.sourceName,
            Boolean.TRUE.equals(entity.lockedByUser));
        return propProduction==null?response:propProduction.reference(tenantId,projectId,response);
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
        String sourceName,
        String sourceType,
        boolean lockedByUser
    ) {
    }
}
