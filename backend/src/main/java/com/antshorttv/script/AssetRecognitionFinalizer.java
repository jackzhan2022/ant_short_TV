package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetRecognitionFinalizer {
    private final JdbcTemplate jdbc;

    public AssetRecognitionFinalizer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void finish(long snapshotId) {
        List<Map<String, Object>> snapshots = jdbc.queryForList("""
            select tenant_id, project_id, script_id from script_analysis_fanout_snapshot
             where id = ? and status in ('RUNNING', 'FINALIZING') for update
            """, snapshotId);
        if (snapshots.isEmpty()) {
            throw new BusinessException(ErrorCode.ANALYSIS_AGENT_INCOMPLETE,
                "资产识别快照不存在或不可收口。");
        }
        Integer missing = jdbc.queryForObject("""
            select count(*) from script_analysis_fanout_unit unit
              left join script_episode_asset_analysis coverage
                on coverage.episode_id = unit.episode_id
               and coverage.generated_by_run_id = unit.child_run_id
             where unit.snapshot_id = ? and (unit.status <> 'SUCCEEDED' or coverage.id is null)
            """, Integer.class, snapshotId);
        if (missing != null && missing > 0) {
            throw new BusinessException(ErrorCode.ANALYSIS_AGENT_INCOMPLETE,
                "尚有剧集未提交当前资产识别结果，不能退役旧资产。");
        }
        Map<String, Object> scope = snapshots.get(0);
        long tenantId = ((Number) scope.get("tenant_id")).longValue();
        long projectId = ((Number) scope.get("project_id")).longValue();
        long scriptId = ((Number) scope.get("script_id")).longValue();

        jdbc.update("""
            update asset_visual_variant_episode binding
               set binding_status = 'RETIRED', retired_at = now(), updated_at = now()
             where binding.tenant_id = ? and binding.project_id = ? and binding.script_id = ?
               and binding.generated_by_run_id is not null and binding.retired_at is null
               and not exists (
                 select 1 from script_analysis_fanout_unit unit
                  where unit.snapshot_id = ? and unit.episode_id = binding.episode_id)
            """, tenantId, projectId, scriptId, snapshotId);
        Map<String, String> types = Map.of(
            "character_asset", "CHARACTER", "scene_asset", "SCENE", "prop_asset", "PROP");
        for (Map.Entry<String, String> entry : types.entrySet()) {
            String table = entry.getKey();
            String type = entry.getValue();
            jdbc.update("update asset_visual_variant variant set deleted_at = now(), updated_at = now()"
                + " where variant.tenant_id = ? and variant.project_id = ?"
                + " and variant.asset_type = ? and variant.generated_by_run_id is not null"
                + " and variant.deleted_at is null"
                + " and exists (select 1 from " + table + " owner where owner.id = variant.asset_id"
                + " and owner.tenant_id = variant.tenant_id and owner.project_id = variant.project_id"
                + " and owner.script_id = ?)"
                + " and not exists (select 1 from asset_visual_variant_episode binding"
                + " where binding.variant_id = variant.id and binding.retired_at is null"
                + " and binding.binding_status = 'ACTIVE')",
                tenantId, projectId, type, scriptId);
            jdbc.update("update " + table + " asset set deleted_at = now(), updated_at = now()"
                + " where asset.tenant_id = ? and asset.project_id = ? and asset.script_id = ?"
                + " and asset.source = 'AI' and asset.generated_by_run_id is not null and asset.deleted_at is null"
                + " and not exists (select 1 from asset_visual_variant_episode binding"
                + " where binding.tenant_id = asset.tenant_id and binding.project_id = asset.project_id"
                + " and binding.asset_type = ? and binding.asset_id = asset.id"
                + " and binding.retired_at is null and binding.binding_status = 'ACTIVE')",
                tenantId, projectId, scriptId, type);
        }
    }
}
