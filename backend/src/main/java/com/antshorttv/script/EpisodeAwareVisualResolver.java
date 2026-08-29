package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EpisodeAwareVisualResolver {
    private final JdbcTemplate jdbc;
    private final AtomicLong legacyFallbackCount = new AtomicLong();

    public EpisodeAwareVisualResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ResolvedVisual resolve(
        Long tenantId, Long projectId, String assetType, Long assetId, Long episodeId
    ) {
        AssetType type = AssetType.fromStorageValue(assetType);
        if (episodeId != null) {
            List<ResolvedVisual> preferred = jdbc.query("""
                select v.id, v.current_image_result_id, v.current_image_url
                  from asset_visual_variant_episode b
                  join script_episode e on e.id = b.episode_id and e.tenant_id = b.tenant_id
                  join asset_visual_variant v on v.id = b.variant_id and v.tenant_id = b.tenant_id
                 where b.tenant_id = ? and b.project_id = ? and b.episode_id = ?
                   and b.asset_type = ? and b.asset_id = ? and b.is_preferred = true
                   and b.binding_status = 'ACTIVE' and b.retired_at is null
                   and e.status = 'ACTIVE' and e.retired_at is null
                   and v.deleted_at is null and v.generation_status = 'COMPLETED'
                   and (v.current_image_result_id is not null or v.current_image_url is not null)
                 limit 1
                """, (rs, rowNum) -> new ResolvedVisual(rs.getLong(1),
                    nullableLong(rs.getObject(2)), rs.getString(3), "EPISODE_PREFERRED"),
                tenantId, projectId, episodeId, type.name(), assetId);
            if (!preferred.isEmpty()) return preferred.get(0);
        }
        List<ResolvedVisual> primary = jdbc.query("""
            select id, current_image_result_id, current_image_url
              from asset_visual_variant
             where tenant_id = ? and project_id = ? and asset_type = ? and asset_id = ?
               and is_primary = true and deleted_at is null and generation_status = 'COMPLETED'
               and (current_image_result_id is not null or current_image_url is not null)
             limit 1
            """, (rs, rowNum) -> new ResolvedVisual(rs.getLong(1),
                nullableLong(rs.getObject(2)), rs.getString(3), "PRIMARY_VARIANT"),
            tenantId, projectId, type.name(), assetId);
        if (!primary.isEmpty()) return primary.get(0);

        String table = switch (type) {
            case CHARACTER -> "character_asset";
            case SCENE -> "scene_asset";
            case PROP -> "prop_asset";
        };
        List<ResolvedVisual> legacy = jdbc.query("select main_image_result_id, main_image_url from " + table
                + " where tenant_id = ? and project_id = ? and id = ? and deleted_at is null limit 1",
            (rs, rowNum) -> new ResolvedVisual(null, nullableLong(rs.getObject(1)), rs.getString(2), "LEGACY_FALLBACK"),
            tenantId, projectId, assetId);
        if (legacy.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "逻辑资产不存在或不属于当前项目。");
        }
        ResolvedVisual fallback = legacy.get(0);
        if (fallback.imageResultId() == null && (fallback.imageUrl() == null || fallback.imageUrl().isBlank())) {
            return new ResolvedVisual(null, null, null, "UNRESOLVED");
        }
        legacyFallbackCount.incrementAndGet();
        return fallback;
    }

    public long legacyFallbackCount() {
        return legacyFallbackCount.get();
    }

    private static Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    public record ResolvedVisual(Long variantId, Long imageResultId, String imageUrl, String source) {}
}
