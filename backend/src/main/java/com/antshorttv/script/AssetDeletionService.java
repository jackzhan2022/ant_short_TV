package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetDeletionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AssetDeletionService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Impact(String assetName, int affectedStoryboardCount, int promptMentionCount) {}
    private record Shot(long id, String document) {}
    private record Target(String type, long id, Set<Long> variants) {}

    public Impact preview(long tenant, long project, String type, long id) {
        String name = requireAsset(tenant, project, type, id, false);
        return process(tenant, project, target(tenant, project, type, id), name, false);
    }

    @Transactional
    public void delete(long tenant, long project, String type, long id) {
        String name = requireAsset(tenant, project, type, id, true);
        process(tenant, project, target(tenant, project, type, id), name, true);
        jdbc.update("update " + table(type) + " set deleted_at=now(),updated_at=now()"
            + " where tenant_id=? and project_id=? and id=? and deleted_at is null", tenant, project, id);
    }

    private Impact process(long tenant, long project, Target target, String name, boolean write) {
        // Read locked documents before changing them, preserving edits completed before deletion.
        List<Shot> shots = jdbc.query("select id,prompt_document_json from storyboard"
                + " where tenant_id=? and project_id=? and deleted_at is null order by id"
                + (write ? " for update" : ""),
            (rs, n) -> new Shot(rs.getLong(1), rs.getString(2)), tenant, project);
        Set<Long> affected = new HashSet<>();
        int mentions = 0;
        for (Shot shot : shots) {
            if (shot.document() == null || shot.document().isBlank()) continue;
            JsonNode document = parse(shot.document());
            int count = replaceMentions(document, target);
            if (count == 0) continue;
            mentions += count;
            affected.add(shot.id());
            if (write) jdbc.update("update storyboard set prompt_document_json=?,updated_at=now()"
                + " where tenant_id=? and project_id=? and id=?", document.toString(), tenant, project, shot.id());
        }
        var references = jdbc.queryForList("""
            select r.id,r.storyboard_id from storyboard_asset_reference r
            join storyboard s on s.id=r.storyboard_id and s.tenant_id=r.tenant_id and s.project_id=r.project_id
            where r.tenant_id=? and r.project_id=? and r.retired_at is null and s.deleted_at is null
              and (r.asset_type=? and r.asset_id=? or r.variant_id in (
                select id from asset_visual_variant where tenant_id=? and project_id=? and asset_type=? and asset_id=?))
            """, tenant, project, target.type(), target.id(), tenant, project, target.type(), target.id());
        for (var reference : references) {
            affected.add(((Number) reference.get("storyboard_id")).longValue());
            if (write) jdbc.update("update storyboard_asset_reference set retired_at=now(),updated_at=now()"
                + " where tenant_id=? and project_id=? and id=? and retired_at is null", tenant, project, reference.get("id"));
        }
        return new Impact(name, affected.size(), mentions);
    }

    private int replaceMentions(JsonNode document, Target target) {
        if (document == null || document.isNull()) return 0;
        StoryboardPromptDocuments.validate(document);
        ArrayNode nodes = (ArrayNode) document.path("nodes");
        int count = 0;
        for (int i = 0; i < nodes.size(); i++) {
            JsonNode node = nodes.get(i);
            if (!"mention".equals(node.path("type").asText())) continue;
            boolean asset = target.type().equals(node.path("assetType").asText())
                && node.path("assetId").asLong() == target.id();
            boolean variant = "ASSET_VISUAL_VARIANT".equals(node.path("sourceType").asText())
                && (target.variants().contains(node.path("sourceId").asLong())
                    || target.variants().contains(node.path("variantId").asLong()));
            if (!asset && !variant) continue;
            nodes.set(i, json.createObjectNode().put("type", "text").put("text", node.path("displayName").asText()));
            count++;
        }
        return count;
    }

    /** A stale editor must not restore deleted references when it next saves. */
    @Transactional
    public JsonNode retainDeletedAssetText(long tenant, long project, JsonNode document) {
        if (document == null || document.isNull()) return document;
        JsonNode updated = document.deepCopy();
        for (String type : List.of("CHARACTER", "SCENE", "PROP")) {
            // Locking/current reads also see a deletion committed while a stale editor waited to save.
            List<Long> deleted = jdbc.queryForList("select id from " + table(type)
                + " where tenant_id=? and project_id=? and deleted_at is not null and merge_target_id is null for update",
                Long.class, tenant, project);
            for (long id : deleted) replaceMentions(updated, target(tenant, project, type, id));
        }
        return updated;
    }

    private Target target(long tenant, long project, String type, long id) {
        return new Target(type, id, new HashSet<>(jdbc.queryForList("select id from asset_visual_variant"
            + " where tenant_id=? and project_id=? and asset_type=? and asset_id=?", Long.class, tenant, project, type, id)));
    }

    private String requireAsset(long tenant, long project, String type, long id, boolean lock) {
        var names = jdbc.queryForList("select name from " + table(type)
            + " where tenant_id=? and project_id=? and id=? and deleted_at is null" + (lock ? " for update" : ""),
            String.class, tenant, project, id);
        if (names.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "资产不存在或已删除，请刷新后重试。");
        return names.get(0);
    }

    private JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (Exception error) { throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分镜提示词文档不正确，删除未完成。"); }
    }

    private String table(String type) {
        return switch (AssetType.fromStorageValue(type)) {
            case CHARACTER -> "character_asset";
            case SCENE -> "scene_asset";
            case PROP -> "prop_asset";
        };
    }
}
