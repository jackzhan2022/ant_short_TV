package com.antshorttv.script;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

final class AssetVisualPrimaryRepair {
    private AssetVisualPrimaryRepair() {
    }

    static void repair(
        JdbcTemplate jdbc, long tenantId, long projectId, long scriptId, String assetType, String assetTable
    ) {
        List<Long> assetIds = jdbc.queryForList("""
            select asset.id from %s asset
             where asset.tenant_id = ? and asset.project_id = ? and asset.script_id = ?
               and asset.deleted_at is null
               and exists (select 1 from asset_visual_variant variant
                 where variant.tenant_id = asset.tenant_id and variant.project_id = asset.project_id
                   and variant.asset_type = ? and variant.asset_id = asset.id
                   and variant.deleted_at is null)
               and not exists (select 1 from asset_visual_variant primary_variant
                 where primary_variant.tenant_id = asset.tenant_id
                   and primary_variant.project_id = asset.project_id
                   and primary_variant.asset_type = ? and primary_variant.asset_id = asset.id
                   and primary_variant.is_primary = true and primary_variant.deleted_at is null)
             order by asset.id
            """.formatted(assetTable), Long.class,
            tenantId, projectId, scriptId, assetType, assetType);
        for (Long assetId : assetIds) {
            List<Long> candidates = jdbc.queryForList("""
                select id from asset_visual_variant
                 where tenant_id = ? and project_id = ? and asset_type = ? and asset_id = ?
                   and deleted_at is null
                 order by case when generation_status = 'COMPLETED'
                    and (current_image_result_id is not null
                      or (current_image_url is not null and trim(current_image_url) <> ''))
                    then 0 else 1 end, id
                 limit 1
                """, Long.class, tenantId, projectId, assetType, assetId);
            if (candidates.isEmpty()) continue;
            jdbc.update("""
                update asset_visual_variant candidate set is_primary = true, updated_at = now()
                 where candidate.id = ? and candidate.tenant_id = ? and candidate.project_id = ?
                   and candidate.is_primary = false and candidate.deleted_at is null
                """, candidates.get(0), tenantId, projectId);
        }
    }
}
