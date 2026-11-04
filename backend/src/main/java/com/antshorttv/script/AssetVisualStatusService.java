package com.antshorttv.script;

import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AssetVisualStatusService {
    private final JdbcTemplate jdbc;
    private final ProjectPermissionGuard permissions;

    public AssetVisualStatusService(JdbcTemplate jdbc, ProjectPermissionGuard permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    public List<Status> read(Long tenantId, Long projectId, List<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty() || variantIds.size() > 100
            || variantIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "每次查询须包含 1–100 个有效视觉形态 ID。");
        }
        permissions.require(tenantId, projectId, "ELEMENT:VIEW");
        List<Long> ids = variantIds.stream().distinct().toList();
        List<Object> args = new ArrayList<>(List.of(tenantId, projectId));
        args.addAll(ids);
        // Project only generation state. A single query also covers mixed asset types;
        // owner checks exclude deleted assets without expanding their visual workspaces.
        List<Status> rows = jdbc.query("""
            select v.id, v.generation_status, v.generation_task_id, v.current_image_result_id,
                   v.generation_error_code, v.generation_error_message,
                   case when v.generation_status = 'COMPLETED' and (
                     (image.status = 'ACTIVE' and image.display_path is not null and image.display_path <> '')
                     or (v.current_image_result_id is null and v.current_image_url is not null
                         and v.current_image_url <> '' and v.current_image_url not like 'data:%')
                     or (v.virtual_avatar_asset_id is not null and v.virtual_avatar_asset_id <> '')
                   ) then true else false end as display_ready
              from asset_visual_variant v
              left join ai_image_result image on image.id = v.current_image_result_id
                and image.tenant_id = v.tenant_id and image.project_id = v.project_id
             where v.tenant_id = ? and v.project_id = ? and v.deleted_at is null and v.id in (
            """ + String.join(",", Collections.nCopies(ids.size(), "?")) + """
            ) and (
              (v.asset_type = 'CHARACTER' and exists (select 1 from character_asset a
                where a.id = v.asset_id and a.tenant_id = v.tenant_id and a.project_id = v.project_id and a.deleted_at is null))
              or (v.asset_type = 'SCENE' and exists (select 1 from scene_asset a
                where a.id = v.asset_id and a.tenant_id = v.tenant_id and a.project_id = v.project_id and a.deleted_at is null))
              or (v.asset_type = 'PROP' and exists (select 1 from prop_asset a
                where a.id = v.asset_id and a.tenant_id = v.tenant_id and a.project_id = v.project_id and a.deleted_at is null))
            )
            """, (row, index) -> new Status(row.getLong("id"), true, row.getString("generation_status"),
                row.getObject("generation_task_id", Long.class), row.getObject("current_image_result_id", Long.class),
                row.getBoolean("display_ready"), row.getString("generation_error_code"), row.getString("generation_error_message")),
            args.toArray());
        var byId = new LinkedHashMap<Long, Status>();
        rows.forEach(row -> byId.put(row.variantId(), row));
        // Unknown, deleted and out-of-scope IDs are indistinguishable.
        return ids.stream().map(id -> byId.getOrDefault(id,
            new Status(id, false, "UNAVAILABLE", null, null, false, null, null))).toList();
    }

    public record Status(Long variantId, boolean available, String generationStatus,
        Long generationTaskId, Long currentImageResultId, boolean displayReady, String errorCode, String errorMessage) {}
}
