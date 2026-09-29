package com.antshorttv.script;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class StoryboardAssetReferenceRepository {
    private static final String ORDER_BY = """
         order by case asset_type when 'CHARACTER' then 0 when 'SCENE' then 1 else 2 end,
                  sort_order, id
        """;

    private final JdbcTemplate jdbc;
    private final StoryboardAssetReferenceMapper mapper;

    public StoryboardAssetReferenceRepository(
        JdbcTemplate jdbc,
        StoryboardAssetReferenceMapper mapper
    ) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<StoryboardAssetReferenceEntity> listActive(
        Long tenantId, Long projectId, Long storyboardId
    ) {
        return jdbc.query("""
            select * from storyboard_asset_reference
             where tenant_id = ? and project_id = ? and storyboard_id = ? and retired_at is null
            """ + ORDER_BY, this::map, tenantId, projectId, storyboardId);
    }

    public Map<Long, List<StoryboardAssetReferenceEntity>> listActiveForStoryboards(
        Long tenantId, Long projectId, List<Long> storyboardIds
    ) {
        if (storyboardIds == null || storyboardIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(storyboardIds.size(), "?"));
        List<Object> arguments = new ArrayList<>(List.of(tenantId, projectId));
        arguments.addAll(storyboardIds);
        List<StoryboardAssetReferenceEntity> rows = jdbc.query("""
            select * from storyboard_asset_reference
             where tenant_id = ? and project_id = ? and retired_at is null
               and storyboard_id in (%s)
            """.formatted(placeholders) + ORDER_BY, this::map, arguments.toArray());
        Map<Long, List<StoryboardAssetReferenceEntity>> grouped = new LinkedHashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(row.storyboardId, ignored -> new ArrayList<>()).add(row));
        return grouped;
    }

    public Map<Long, List<StoryboardAssetReferenceResponse>> listResponsesForStoryboards(
        Long tenantId, Long projectId, List<Long> storyboardIds
    ) {
        if (storyboardIds == null || storyboardIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(storyboardIds.size(), "?"));
        List<Object> arguments = new ArrayList<>(List.of(tenantId, projectId));
        arguments.addAll(storyboardIds);
        List<Map.Entry<Long, StoryboardAssetReferenceResponse>> rows = jdbc.query("""
            select reference.storyboard_id,reference.id,reference.asset_type,reference.asset_id,
                   coalesce(character.name,scene.name,prop.name) asset_name,
                   reference.variant_id,variant.name variant_name,variant.current_image_url,
                   reference.reference_role,reference.sort_order,reference.resolution_status,
                   reference.source_type,reference.source_name,reference.locked_by_user
              from storyboard_asset_reference reference
              left join character_asset character on reference.asset_type='CHARACTER'
                and character.id=reference.asset_id and character.tenant_id=reference.tenant_id
                and character.project_id=reference.project_id and character.deleted_at is null
              left join scene_asset scene on reference.asset_type='SCENE'
                and scene.id=reference.asset_id and scene.tenant_id=reference.tenant_id
                and scene.project_id=reference.project_id and scene.deleted_at is null
              left join prop_asset prop on reference.asset_type='PROP'
                and prop.id=reference.asset_id and prop.tenant_id=reference.tenant_id
                and prop.project_id=reference.project_id and prop.deleted_at is null
              left join asset_visual_variant variant on variant.id=reference.variant_id
                and variant.tenant_id=reference.tenant_id and variant.project_id=reference.project_id
                and variant.deleted_at is null
             where reference.tenant_id=? and reference.project_id=? and reference.retired_at is null
               and reference.storyboard_id in (%s)
             order by case reference.asset_type
                        when 'CHARACTER' then 0 when 'SCENE' then 1 else 2 end,
                      reference.sort_order, reference.id
            """.formatted(placeholders),
            (rs, rowNum) -> Map.entry(rs.getLong("storyboard_id"),
                new StoryboardAssetReferenceResponse(
                    rs.getLong("id"), rs.getString("asset_type"),
                    rs.getObject("asset_id", Long.class), rs.getString("asset_name"),
                    rs.getObject("variant_id", Long.class), rs.getString("variant_name"),
                    rs.getString("current_image_url"), rs.getString("reference_role"),
                    rs.getInt("sort_order"), rs.getString("resolution_status"),
                    rs.getString("source_type"), rs.getString("source_name"),
                    rs.getBoolean("locked_by_user"))), arguments.toArray());
        Map<Long, List<StoryboardAssetReferenceResponse>> grouped = new LinkedHashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(row.getKey(), ignored -> new ArrayList<>())
            .add(row.getValue()));
        return grouped;
    }

    @Transactional
    public void replace(
        Long tenantId,
        Long projectId,
        Long storyboardId,
        List<StoryboardAssetReferenceEntity> replacements,
        LocalDateTime now
    ) {
        jdbc.update("""
            update storyboard_asset_reference
               set retired_at = ?, updated_at = ?
             where tenant_id = ? and project_id = ? and storyboard_id = ? and retired_at is null
            """, now, now, tenantId, projectId, storyboardId);
        if (replacements == null) return;
        for (StoryboardAssetReferenceEntity entity : replacements) {
            entity.id = null;
            entity.tenantId = tenantId;
            entity.projectId = projectId;
            entity.storyboardId = storyboardId;
            entity.createdAt = now;
            entity.updatedAt = now;
            entity.retiredAt = null;
            mapper.insert(entity);
        }
    }

    public void retireForStoryboards(
        Long tenantId, Long projectId, List<Long> storyboardIds, LocalDateTime now
    ) {
        if (storyboardIds == null || storyboardIds.isEmpty()) return;
        String placeholders = String.join(",", Collections.nCopies(storyboardIds.size(), "?"));
        List<Object> arguments = new ArrayList<>(List.of(now, now, tenantId, projectId));
        arguments.addAll(storyboardIds);
        jdbc.update("""
            update storyboard_asset_reference set retired_at=?,updated_at=?
             where tenant_id=? and project_id=? and retired_at is null
               and storyboard_id in (%s)
            """.formatted(placeholders), arguments.toArray());
    }

    private StoryboardAssetReferenceEntity map(ResultSet rs, int rowNumber) throws SQLException {
        StoryboardAssetReferenceEntity entity = new StoryboardAssetReferenceEntity();
        entity.id = rs.getLong("id");
        entity.tenantId = rs.getLong("tenant_id");
        entity.projectId = rs.getLong("project_id");
        entity.storyboardId = rs.getLong("storyboard_id");
        entity.assetType = rs.getString("asset_type");
        entity.assetId = rs.getObject("asset_id", Long.class);
        entity.variantId = rs.getObject("variant_id", Long.class);
        entity.referenceRole = rs.getString("reference_role");
        entity.sortOrder = rs.getInt("sort_order");
        entity.resolutionStatus = rs.getString("resolution_status");
        entity.sourceType = rs.getString("source_type");
        entity.sourceName = rs.getString("source_name");
        entity.lockedByUser = rs.getBoolean("locked_by_user");
        entity.generatedByRunId = rs.getObject("generated_by_run_id", Long.class);
        entity.createdBy = rs.getLong("created_by");
        entity.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
        entity.updatedAt = rs.getTimestamp("updated_at").toLocalDateTime();
        var retiredAt = rs.getTimestamp("retired_at");
        entity.retiredAt = retiredAt == null ? null : retiredAt.toLocalDateTime();
        return entity;
    }
}
