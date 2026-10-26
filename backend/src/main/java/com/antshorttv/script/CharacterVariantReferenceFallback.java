package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;

/** Replaces editable storyboard references only; generated media and submitted task snapshots remain historical. */
public final class CharacterVariantReferenceFallback {
    private static final ObjectMapper JSON = new ObjectMapper();

    private CharacterVariantReferenceFallback() {}

    static void lockEditableReferences(JdbcTemplate jdbc,long tenantId,long projectId,long variantId) {
        jdbc.queryForList("""
            select s.id from storyboard s where s.tenant_id=? and s.project_id=? and s.deleted_at is null
              and (s.prompt_document_json like ? or exists (
                select 1 from storyboard_asset_reference r where r.storyboard_id=s.id
                  and r.tenant_id=s.tenant_id and r.project_id=s.project_id and r.variant_id=? and r.retired_at is null))
             order by s.id for update
            """,Long.class,tenantId,projectId,"%"+variantId+"%",variantId);
    }

    static com.fasterxml.jackson.databind.JsonNode normalizeDocument(JdbcTemplate jdbc,long tenantId,long projectId,
        com.fasterxml.jackson.databind.JsonNode document) {
        if (document == null || document.isNull()) return document;
        var normalized = document.deepCopy();
        for (var node : normalized.path("nodes")) {
            if (!(node instanceof ObjectNode mention) || !"mention".equals(node.path("type").asText())
                || !"IMAGE".equals(node.path("mediaType").asText())
                || !"ASSET_VISUAL_VARIANT".equals(node.path("sourceType").asText())) continue;
            var origins=jdbc.query("""
                select asset_id,asset_type,deleted_at from asset_visual_variant
                 where id=? and tenant_id=? and project_id=? for update
                """,(rs,n)->new Origin(rs.getLong(1),rs.getString(2),rs.getObject(3)!=null),
                node.path("sourceId").asLong(),tenantId,projectId);
            if (origins.isEmpty() || !origins.get(0).deleted() || !"CHARACTER".equals(origins.get(0).type())) continue;
            var origin=origins.get(0);
            if ((node.hasNonNull("assetId") && node.path("assetId").asLong()!=origin.assetId())
                || (node.hasNonNull("assetType") && !"CHARACTER".equals(node.path("assetType").asText())))
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,"视觉形象不属于所选人物。");
            var defaults=jdbc.query("""
                select v.id,v.name from asset_visual_variant v
                 where v.tenant_id=? and v.project_id=? and v.asset_type='CHARACTER' and v.asset_id=?
                   and v.deleted_at is null and v.is_primary=true order by v.id limit 1 for update
                """,(rs,n)->new DefaultVisual(rs.getLong(1),rs.getString(2)),tenantId,projectId,origin.assetId());
            if (defaults.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR,"所选人物形态已删除，且暂无默认形象，请重新选择。");
            mention.put("sourceId",defaults.get(0).id());mention.put("variantId",defaults.get(0).id());
            String assetName=jdbc.queryForObject("select name from character_asset where id=? and tenant_id=? and project_id=? and deleted_at is null",
                String.class,origin.assetId(),tenantId,projectId);
            mention.put("assetId",origin.assetId());mention.put("assetType","CHARACTER");mention.put("displayName",assetName+"·"+defaults.get(0).name());
            mention.remove(java.util.List.of("imageUrl","thumbnailUrl","previewUrl"));
        }
        return normalized;
    }

    public static void replace(JdbcTemplate jdbc, long tenantId, long projectId, long assetId,
        long deletedId, long primaryId, String displayName, boolean usable) {
        jdbc.update("""
            update storyboard_asset_reference set variant_id=?, resolution_status=?, updated_at=now()
             where tenant_id=? and project_id=? and asset_type='CHARACTER' and asset_id=?
               and variant_id=? and retired_at is null
               and storyboard_id in (select id from storyboard where tenant_id=? and project_id=? and deleted_at is null)
            """, primaryId, usable ? "RESOLVED" : "ASSET_PENDING", tenantId, projectId, assetId, deletedId, tenantId, projectId);
        var documents = jdbc.query("""
            select id,prompt_document_json from storyboard
             where tenant_id=? and project_id=? and deleted_at is null and prompt_document_json like ? order by id for update
            """, (rs,n) -> new Document(rs.getLong(1),rs.getString(2)), tenantId,projectId,"%"+deletedId+"%");
        for (var document : documents) {
            try {
                var tree = JSON.readTree(document.json());
                if (tree == null || !tree.path("nodes").isArray()) continue;
                boolean changed = false;
                for (var node : tree.path("nodes")) {
                    if (!(node instanceof ObjectNode mention)
                        || !"mention".equals(node.path("type").asText())
                        || !"IMAGE".equals(node.path("mediaType").asText())
                        || !"ASSET_VISUAL_VARIANT".equals(node.path("sourceType").asText())
                        || node.path("sourceId").asLong() != deletedId
                        || (node.hasNonNull("assetId") && node.path("assetId").asLong() != assetId)
                        || (node.hasNonNull("assetType") && !"CHARACTER".equals(node.path("assetType").asText()))) continue;
                    mention.put("sourceId",primaryId);
                    mention.put("variantId",primaryId);
                    mention.put("assetId",assetId);
                    mention.put("assetType","CHARACTER");
                    mention.put("displayName",displayName);
                    // Thumbnails are resolved from the new variant, never from stale saved preview fields.
                    mention.remove(java.util.List.of("imageUrl","thumbnailUrl","previewUrl"));
                    changed = true;
                }
                if (changed && jdbc.update("""
                    update storyboard set prompt_document_json=?,updated_at=now()
                     where id=? and tenant_id=? and project_id=? and deleted_at is null and prompt_document_json=?
                    """, JSON.writeValueAsString(tree),document.id(),tenantId,projectId,document.json()) != 1) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,"分镜已被其他操作更新，请刷新后重试删除。");
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException invalidDocument) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,"分镜提示词格式异常，请修复后再删除形态。");
            }
        }
    }

    public static void repairHistorical(JdbcTemplate jdbc) {
        var fallbacks = jdbc.query("""
            select old.tenant_id,old.project_id,old.asset_id,old.id,primary_variant.id,
                   asset.name,primary_variant.name,primary_variant.generation_status,
                   primary_variant.current_image_result_id,primary_variant.current_image_url
              from asset_visual_variant old
              join character_asset asset on asset.id=old.asset_id and asset.tenant_id=old.tenant_id
               and asset.project_id=old.project_id and asset.deleted_at is null
              join asset_visual_variant primary_variant on primary_variant.tenant_id=old.tenant_id
               and primary_variant.project_id=old.project_id and primary_variant.asset_type='CHARACTER'
               and primary_variant.asset_id=old.asset_id and primary_variant.is_primary=true and primary_variant.deleted_at is null
             where old.asset_type='CHARACTER' and old.deleted_at is not null
             order by old.tenant_id,old.project_id,old.id,primary_variant.id
            """, (rs,n) -> new Fallback(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),
                rs.getString(6)+"·"+rs.getString(7),"COMPLETED".equals(rs.getString(8))
                    && (rs.getObject(9)!=null || (rs.getString(10)!=null && !rs.getString(10).isBlank()))));
        for (var fallback : fallbacks) replace(jdbc,fallback.tenantId(),fallback.projectId(),fallback.assetId(),
            fallback.deletedId(),fallback.primaryId(),fallback.displayName(),fallback.usable());
    }

    private record Origin(long assetId,String type,boolean deleted) {}
    private record DefaultVisual(long id,String name) {}
    private record Document(long id,String json) {}
    private record Fallback(long tenantId,long projectId,long assetId,long deletedId,long primaryId,String displayName,boolean usable) {}
}
