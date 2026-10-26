package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** Display-name edits deliberately leave normalized identities and generated media untouched. */
@Service
public class AssetNameService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AssetNameService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void renameAsset(long tenantId, long projectId, String assetType, long assetId, String requestedName) {
        String name = validatedName(requestedName);
        ScriptElementType type = ScriptElementType.from(assetType);
        String table = type.tableName();
        String previousName = lockAsset(tenantId, projectId, type, assetId);
        var variantIds = jdbc.queryForList("""
            select id from asset_visual_variant where tenant_id=? and project_id=?
             and asset_type=? and asset_id=? and deleted_at is null order by id
            """, Long.class, tenantId, projectId, type.name(), assetId);
        var documents = lockDocuments(tenantId, projectId, variantIds);
        Map<Long, Label> labels = variantLabels(tenantId, projectId, type, assetId, previousName, name, null, null);
        requireUpdated(jdbc.update("update " + table + " set name=?, updated_at=now()"
            + " where id=? and tenant_id=? and project_id=? and deleted_at is null", name, assetId, tenantId, projectId));
        renameMentions(tenantId, projectId, type, assetId, labels, documents);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void renameVariant(long tenantId, long projectId, long variantId, String requestedName) {
        String name = validatedName(requestedName);
        var origins = jdbc.query("""
            select asset_type,asset_id from asset_visual_variant
             where id=? and tenant_id=? and project_id=? and deleted_at is null
            """, (rs, n) -> new Owner(ScriptElementType.from(rs.getString(1)), rs.getLong(2)), variantId, tenantId, projectId);
        if (origins.isEmpty()) throw missing();
        Owner owner = origins.get(0);
        // Asset-first ordering serializes asset/variant renames and character variant deletion.
        String assetName = lockAsset(tenantId, projectId, owner.type(), owner.id());
        var documents = lockDocuments(tenantId, projectId, List.of(variantId));
        Map<Long, Label> labels = variantLabels(tenantId, projectId, owner.type(), owner.id(), assetName, assetName, variantId, name);
        if (!labels.containsKey(variantId)) throw missing();
        requireUpdated(jdbc.update("""
            update asset_visual_variant set name=?,updated_at=now()
             where id=? and tenant_id=? and project_id=? and asset_type=? and asset_id=? and deleted_at is null
            """, name, variantId, tenantId, projectId, owner.type().name(), owner.id()));
        renameMentions(tenantId, projectId, owner.type(), owner.id(), labels, documents);
    }

    /** Called before any episode/storyboard lock, inside the existing save transaction. */
    public DocumentAssets lockDocumentAssets(long tenantId, long projectId, JsonNode document) {
        if (document == null || document.isNull()) return new DocumentAssets(Map.of(), Map.of());
        var owners = new java.util.TreeSet<Owner>(java.util.Comparator
            .comparing((Owner owner) -> owner.type().name()).thenComparingLong(Owner::id));
        var seen = new java.util.HashSet<Long>();
        Map<Long, Owner> originalOwners = new LinkedHashMap<>();
        for (var node : document.path("nodes")) {
            if (!variantMention(node) || !seen.add(node.path("sourceId").asLong())) continue;
            var matches = jdbc.query("""
                select asset_type,asset_id from asset_visual_variant where id=? and tenant_id=? and project_id=?
                """, (rs, n) -> new Owner(ScriptElementType.from(rs.getString(1)), rs.getLong(2)),
                node.path("sourceId").asLong(), tenantId, projectId);
            if (matches.isEmpty()) continue;
            owners.add(matches.get(0));
            originalOwners.put(node.path("sourceId").asLong(), matches.get(0));
        }
        Map<Owner, String> names = new LinkedHashMap<>();
        for (Owner owner : owners) names.put(owner, lockAsset(tenantId, projectId, owner.type(), owner.id()));
        return new DocumentAssets(Map.copyOf(names), Map.copyOf(originalOwners));
    }

    /** Only display labels are refreshed; caller has already locked all referenced assets. */
    public JsonNode normalizeDocument(long tenantId, long projectId, JsonNode document, DocumentAssets assets) {
        if (document == null || document.isNull()) return document;
        var normalized = document.deepCopy();
        for (var node : normalized.path("nodes")) {
            if (!(node instanceof ObjectNode mention) || !variantMention(node)) continue;
            long sourceId = node.path("sourceId").asLong();
            var matches = jdbc.query("""
                select asset_type,asset_id,name,deleted_at from asset_visual_variant
                 where id=? and tenant_id=? and project_id=? for update
                """, (rs, n) -> new NamedVariant(new Owner(ScriptElementType.from(rs.getString(1)), rs.getLong(2)),
                    rs.getString(3), rs.getObject(4) != null), sourceId, tenantId, projectId);
            if (matches.isEmpty()) continue;
            var variant = matches.get(0);
            String assetName = assets.names().get(variant.owner());
            Owner originalOwner = assets.originalOwners().get(sourceId);
            if (assetName == null
                || (originalOwner != null && !originalOwner.equals(variant.owner()))
                || (node.hasNonNull("assetType") && !variant.owner().type().name().equals(node.path("assetType").asText()))
                || (node.hasNonNull("assetId") && variant.owner().id() != node.path("assetId").asLong())
                || (node.hasNonNull("variantId") && sourceId != node.path("variantId").asLong()))
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "素材归属已变化，请刷新后重新选择。");
            // Deleted-character fallback runs first. Other deleted-variant behavior stays unchanged.
            if (variant.deleted()) continue;
            mention.put("displayName", assetName + " / " + variant.name());
        }
        return normalized;
    }

    private boolean variantMention(JsonNode node) {
        return "mention".equals(node.path("type").asText())
            && "IMAGE".equals(node.path("mediaType").asText())
            && "ASSET_VISUAL_VARIANT".equals(node.path("sourceType").asText());
    }
    private String lockAsset(long tenantId, long projectId, ScriptElementType type, long assetId) {
        var names = jdbc.queryForList("select name from " + type.tableName()
            + " where id=? and tenant_id=? and project_id=? and deleted_at is null for update",
            String.class, assetId, tenantId, projectId);
        if (names.isEmpty()) throw missing();
        return names.get(0);
    }

    private Map<Long, Label> variantLabels(long tenantId, long projectId, ScriptElementType type, long assetId,
        String previousAssetName, String assetName, Long variantId, String variantName) {
        var variants = jdbc.query("""
            select id,name from asset_visual_variant where tenant_id=? and project_id=?
             and asset_type=? and asset_id=? and deleted_at is null order by id for update
            """, (rs, n) -> new Variant(rs.getLong(1), rs.getString(2)), tenantId, projectId, type.name(), assetId);
        Map<Long, Label> labels = new LinkedHashMap<>();
        for (var variant : variants) {
            if (variantId == null || variant.id() == variantId)
                labels.put(variant.id(), new Label(previousAssetName, variant.name(), assetName,
                    variantId == null ? variant.name() : variantName));
        }
        return labels;
    }

    private List<Document> lockDocuments(long tenantId, long projectId, List<Long> variantIds) {
        if (variantIds.isEmpty()) return List.of();
        List<Object> arguments = new ArrayList<>(List.of(tenantId, projectId));
        variantIds.forEach(id -> arguments.add("%" + id + "%"));
        String filter = String.join(" or ", java.util.Collections.nCopies(variantIds.size(), "prompt_document_json like ?"));
        // Match updateStoryboard's storyboard-before-variant order and read the latest committed document.
        return jdbc.query("select id,prompt_document_json from storyboard where tenant_id=? and project_id=?"
            + " and deleted_at is null and (" + filter + ") order by id for update",
            (rs, n) -> new Document(rs.getLong(1), rs.getString(2)), arguments.toArray());
    }

    private void renameMentions(long tenantId, long projectId, ScriptElementType type, long assetId,
        Map<Long, Label> labels, List<Document> documents) {
        for (var document : documents) {
            try {
                var tree = json.readTree(document.json());
                if (tree == null || !tree.path("nodes").isArray()) continue;
                boolean changed = false;
                for (var node : tree.path("nodes")) {
                    if (!(node instanceof ObjectNode mention)
                        || !"mention".equals(node.path("type").asText())
                        || !"IMAGE".equals(node.path("mediaType").asText())
                        || !"ASSET_VISUAL_VARIANT".equals(node.path("sourceType").asText())
                        || (node.hasNonNull("assetType") && !type.name().equals(node.path("assetType").asText()))
                        || (node.hasNonNull("assetId") && node.path("assetId").asLong() != assetId)) continue;
                    long sourceId = node.path("sourceId").asLong();
                    if (node.hasNonNull("variantId") && node.path("variantId").asLong() != sourceId) continue;
                    Label value = labels.get(sourceId);
                    if (value == null) continue;
                    String previous = node.path("displayName").asText();
                    String label = value.format(previous);
                    if (label.equals(previous)) continue;
                    mention.put("displayName", label);
                    changed = true;
                }
                if (changed && jdbc.update("""
                    update storyboard set prompt_document_json=?,updated_at=now()
                     where id=? and tenant_id=? and project_id=? and deleted_at is null and prompt_document_json=?
                    """, json.writeValueAsString(tree), document.id(), tenantId, projectId, document.json()) != 1)
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分镜已被其他操作更新，请刷新后重试改名。");
            } catch (JsonProcessingException invalidDocument) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分镜提示词格式异常，请修复后再修改名称。");
            }
        }
    }

    private String validatedName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "名称不能为空。");
        if (name.length() > 100) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "名称不能超过100个字符。");
        return name;
    }

    private void requireUpdated(int count) {
        if (count != 1) throw missing();
    }

    private BusinessException missing() {
        return new BusinessException(ErrorCode.NOT_FOUND, "资产或形态不存在，或不属于当前项目。");
    }

    private record Label(String previousAssetName, String previousVariantName, String assetName, String variantName) {
        String format(String previous) {
            // Retain legacy separators without replacing characters inside the asset or variant names.
            String separator = previous.equals(previousAssetName + "·" + previousVariantName) ? "·" : " / ";
            return assetName + separator + variantName;
        }
    }

    record DocumentAssets(Map<Owner, String> names, Map<Long, Owner> originalOwners) {}
    record Owner(ScriptElementType type, long id) {}
    private record NamedVariant(Owner owner, String name, boolean deleted) {}
    private record Variant(long id, String name) {}
    private record Document(long id, String json) {}
}
