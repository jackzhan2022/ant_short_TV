package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.workflowagent.tool.ToolExecutionContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EpisodeAssetPersistenceService {
    private static final Map<String, String> TABLES = Map.of(
        "CHARACTER", "character_asset", "SCENE", "scene_asset", "PROP", "prop_asset");
    private static final Map<String, String> PREFIXES = Map.of(
        "CHARACTER", "c_", "SCENE", "s_", "PROP", "p_");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public EpisodeAssetPersistenceService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public JsonNode save(ToolExecutionContext context, JsonNode payload) {
        ReadEpisode read = requireReadEpisode(context);
        Map<String, ResolvedAsset> characters = resolveIdentities(
            context, read.content(), "CHARACTER", payload.path("characters"));
        Map<String, ResolvedAsset> scenes = resolveIdentities(
            context, read.content(), "SCENE", payload.path("scenes"));
        Map<String, ResolvedAsset> props = resolveIdentities(
            context, read.content(), "PROP", payload.path("props"));
        validatePropOwners(payload.path("props"), characters);

        List<Binding> bindings = new ArrayList<>();
        bindExplicitVariants(context, read.content(), "CHARACTER", "characterLocalKey",
            payload.path("characterLooks"), characters, bindings);
        bindExplicitVariants(context, read.content(), "PROP", "propLocalKey",
            payload.path("propVariants"), props, bindings);
        addDefaultBindings(context, "CHARACTER", characters, bindings);
        addDefaultBindings(context, "SCENE", scenes, bindings);
        addDefaultBindings(context, "PROP", props, bindings);
        applySceneUsage(payload.path("scenes"), scenes, bindings, read.content());
        enforcePreferred(bindings);
        replaceBindings(context, bindings);

        ObjectNode counts = json.createObjectNode();
        counts.put("characters", characters.size());
        counts.put("characterLooks", payload.path("characterLooks").size());
        counts.put("scenes", scenes.size());
        counts.put("props", props.size());
        counts.put("propVariants", payload.path("propVariants").size());
        ObjectNode diagnostic = json.createObjectNode();
        diagnostic.set("counts", counts.deepCopy());
        diagnostic.put("bindingCount", bindings.size());
        long analysisId = upsertCoverage(context, payload.path("schemaVersion").asInt(),
            read.fingerprint(), diagnostic);

        ObjectNode result = json.createObjectNode();
        result.put("saved", true);
        result.put("analysisId", analysisId);
        result.put("episodeKey", read.episodeKey());
        result.put("contentFingerprint", read.fingerprint());
        result.set("counts", counts);
        return result;
    }

    public boolean hasCoverage(Long tenantId, Long scriptId, Long episodeId, Long runId) {
        Integer count = jdbc.queryForObject("""
            select count(*) from script_episode_asset_analysis
             where tenant_id = ? and script_id = ? and episode_id = ? and generated_by_run_id = ?
            """, Integer.class, tenantId, scriptId, episodeId, runId);
        return count != null && count == 1;
    }

    private ReadEpisode requireReadEpisode(ToolExecutionContext context) {
        Long readEpisodeId;
        String expectedFingerprint;
        String expectedContentHash;
        try {
            readEpisodeId = context.runState().require("currentEpisodeId", Long.class);
            expectedFingerprint = context.runState().require("currentEpisodeFingerprint", String.class);
            expectedContentHash = context.runState().require("currentEpisodeContentHash", String.class);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.REQUIRED_TOOL_NOT_CALLED,
                "保存资产前必须先读取当前剧集。");
        }
        if (!readEpisodeId.equals(context.episodeId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "读取剧集与保存作用域不一致。");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select stable_key, content, content_fingerprint from script_episode
             where id = ? and tenant_id = ? and project_id = ? and script_id = ?
               and status = 'ACTIVE' and retired_at is null for update
            """, context.episodeId(), context.tenantId(), context.projectId(), context.scriptId());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前正式剧集不存在或已退役。");
        }
        Map<String, Object> row = rows.get(0);
        String content = String.valueOf(row.getOrDefault("content", ""));
        String fingerprint = String.valueOf(row.get("content_fingerprint"));
        if (!expectedFingerprint.equals(fingerprint) || !expectedContentHash.equals(sha256(content))) {
            throw new BusinessException(ErrorCode.EPISODE_CONTENT_CHANGED,
                "剧集在资产识别过程中发生变化，请重新读取后再分析。");
        }
        return new ReadEpisode(String.valueOf(row.get("stable_key")), content, fingerprint);
    }

    private Map<String, ResolvedAsset> resolveIdentities(
        ToolExecutionContext context, String content, String type, JsonNode items
    ) {
        Map<String, ResolvedAsset> resolved = new LinkedHashMap<>();
        Set<String> localKeys = new HashSet<>();
        for (JsonNode item : items) {
            String localKey = item.path("localKey").asText();
            if (!localKeys.add(localKey)) {
                invalid("运行内资产 key 重复：" + localKey);
            }
            requireEvidence(content, item.path("evidence").asText(), "资产 " + localKey);
            ArrayNode aliases = (ArrayNode) item.path("aliases");
            for (JsonNode alias : aliases) {
                requireEvidence(content, alias.path("evidence").asText(), "别名 " + alias.path("name").asText());
            }
            ResolvedAsset asset = resolveIdentity(context, type, item);
            resolved.put(localKey, asset);
        }
        return resolved;
    }

    private ResolvedAsset resolveIdentity(ToolExecutionContext context, String type, JsonNode item) {
        String name = item.path("name").asText().trim();
        String normalized = AssetIdentityNormalizer.normalize(name);
        if (normalized.isBlank()) invalid("资产规范名不能为空。");
        String trustedKey = item.path("assetKey").isTextual() ? item.path("assetKey").asText() : null;
        if (trustedKey != null && !trustedKey.isBlank()) {
            long id = parseOpaqueKey(trustedKey, PREFIXES.get(type));
            List<Map<String, Object>> rows = findById(context, type, id);
            if (rows.size() != 1) invalid("资产 key 不属于当前剧本或类型不匹配。");
            updateMetadata(context, type, id, item, normalized, false);
            return new ResolvedAsset(id, item.path("localKey").asText(), type);
        }

        lockIdentity(context, type, normalized);
        List<Map<String, Object>> matches = findExactMatches(context, type, normalized);
        if (matches.size() > 1) {
            String candidates = matches.stream().map(row -> PREFIXES.get(type) + row.get("id")).toList().toString();
            throw new BusinessException(ErrorCode.ENTITY_MATCH_AMBIGUOUS,
                "资产匹配不唯一，可选安全 key：" + candidates);
        }
        long id;
        boolean created;
        if (matches.isEmpty()) {
            id = insertIdentity(context, type, item, normalized);
            created = true;
        } else {
            id = ((Number) matches.get(0).get("id")).longValue();
            created = false;
        }
        updateMetadata(context, type, id, item, normalized, created);
        return new ResolvedAsset(id, item.path("localKey").asText(), type);
    }

    private List<Map<String, Object>> findById(ToolExecutionContext context, String type, long id) {
        return jdbc.queryForList("select id, name, normalized_name, content_json from " + TABLES.get(type)
                + " where id = ? and tenant_id = ? and project_id = ? and script_id = ? and deleted_at is null for update",
            id, context.tenantId(), context.projectId(), context.scriptId());
    }

    private List<Map<String, Object>> findExactMatches(
        ToolExecutionContext context, String type, String normalized
    ) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select id, name, normalized_name, content_json from " + TABLES.get(type)
                + " where tenant_id = ? and project_id = ? and script_id = ? and deleted_at is null for update",
            context.tenantId(), context.projectId(), context.scriptId());
        return rows.stream().filter(row -> normalized.equals(String.valueOf(row.get("normalized_name")))
            || aliases(row.get("content_json")).contains(normalized)).toList();
    }

    private Set<String> aliases(Object raw) {
        Set<String> result = new HashSet<>();
        if (raw == null) return result;
        try {
            JsonNode node = json.readTree(String.valueOf(raw)).path("aliases");
            for (JsonNode alias : node) {
                String value = alias.isTextual() ? alias.asText() : alias.path("name").asText();
                result.add(AssetIdentityNormalizer.normalize(value));
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("资产元数据损坏。", exception);
        }
    }

    private void lockIdentity(ToolExecutionContext context, String type, String normalized) {
        jdbc.update("""
            insert into script_asset_identity_lock
              (tenant_id, project_id, script_id, asset_type, normalized_name, created_at)
            values (?, ?, ?, ?, ?, now())
            on duplicate key update id = id
            """, context.tenantId(), context.projectId(), context.scriptId(), type, normalized);
        jdbc.queryForObject("""
            select id from script_asset_identity_lock
             where tenant_id = ? and project_id = ? and script_id = ?
               and asset_type = ? and normalized_name = ? for update
            """, Long.class, context.tenantId(), context.projectId(), context.scriptId(), type, normalized);
    }

    private long insertIdentity(ToolExecutionContext context, String type, JsonNode item, String normalized) {
        String table = TABLES.get(type);
        String columns;
        String values;
        List<Object> args = new ArrayList<>(List.of(
            context.tenantId(), context.projectId(), context.scriptId(), item.path("name").asText().trim(), normalized));
        if ("CHARACTER".equals(type)) {
            columns = "role_type";
            values = "'SUPPORTING'";
        } else if ("SCENE".equals(type)) {
            columns = "scene_type";
            values = "'LOCATION'";
        } else {
            columns = "prop_type";
            values = "'KEY_PROP'";
        }
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("insert into " + table
                + " (tenant_id, project_id, script_id, name, normalized_name, " + columns
                + ", status, source, generated_by_run_id, created_by, created_at, updated_at)"
                + " values (?, ?, ?, ?, ?, " + values + ", 'CONFIRMED', 'AI', ?, ?, now(), now())",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            for (Object arg : args) statement.setObject(i++, arg);
            statement.setObject(i++, context.agentRunId());
            statement.setLong(i, context.userId());
            return statement;
        }, key);
        Number id = generatedId(key);
        if (id == null) throw new IllegalStateException("资产 id 未生成。");
        return id.longValue();
    }

    private void updateMetadata(
        ToolExecutionContext context, String type, long id, JsonNode item, String normalized, boolean created
    ) {
        if (!created) {
            mergeExistingMetadata(type, id, item);
            return;
        }
        ObjectNode metadata = json.createObjectNode();
        metadata.set("aliases", item.path("aliases").deepCopy());
        metadata.put("evidence", item.path("evidence").asText());
        if (item.has("description") && !item.path("description").isNull()) {
            metadata.put("description", item.path("description").asText());
        }
        if (item.has("ownerCharacterLocalKey") && !item.path("ownerCharacterLocalKey").isNull()) {
            metadata.put("ownerCharacterLocalKey", item.path("ownerCharacterLocalKey").asText());
        }
        String runSql = ", source = 'AI', generated_by_run_id = ?";
        List<Object> args = new ArrayList<>(List.of(
            item.path("name").asText().trim(), normalized, metadata.toString()));
        args.add(context.agentRunId());
        args.add(id);
        jdbc.update("update " + TABLES.get(type)
            + " set name = ?, normalized_name = ?, content_json = ?" + runSql
            + ", updated_at = now() where id = ?", args.toArray());
    }

    private void mergeExistingMetadata(String type, long id, JsonNode item) {
        Map<String, Object> row = jdbc.queryForMap(
            "select name, normalized_name, content_json from " + TABLES.get(type) + " where id = ? for update", id);
        ObjectNode metadata = json.createObjectNode();
        Object raw = row.get("content_json");
        if (raw != null) {
            try {
                JsonNode existing = json.readTree(String.valueOf(raw));
                if (existing.isObject()) metadata.setAll((ObjectNode) existing);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("资产元数据损坏。", exception);
            }
        }
        Map<String, JsonNode> aliasesByName = new LinkedHashMap<>();
        for (JsonNode alias : metadata.path("aliases")) {
            String name = alias.isTextual() ? alias.asText() : alias.path("name").asText();
            if (!name.isBlank()) aliasesByName.put(AssetIdentityNormalizer.normalize(name), alias.deepCopy());
        }
        for (JsonNode alias : item.path("aliases")) {
            String name = alias.path("name").asText();
            if (!name.isBlank()) aliasesByName.putIfAbsent(AssetIdentityNormalizer.normalize(name), alias.deepCopy());
        }
        String canonicalNormalized = String.valueOf(row.get("normalized_name"));
        String proposedName = item.path("name").asText().trim();
        if (!proposedName.isBlank() && !canonicalNormalized.equals(AssetIdentityNormalizer.normalize(proposedName))) {
            ObjectNode alias = json.createObjectNode();
            alias.put("name", proposedName);
            alias.put("evidence", item.path("evidence").asText());
            aliasesByName.putIfAbsent(AssetIdentityNormalizer.normalize(proposedName), alias);
        }
        ArrayNode aliases = json.createArrayNode();
        aliasesByName.values().forEach(aliases::add);
        metadata.set("aliases", aliases);
        metadata.put("lastEvidence", item.path("evidence").asText());
        jdbc.update("update " + TABLES.get(type) + " set content_json = ?, updated_at = now() where id = ?",
            metadata.toString(), id);
    }

    private void validatePropOwners(JsonNode props, Map<String, ResolvedAsset> characters) {
        for (JsonNode prop : props) {
            JsonNode owner = prop.path("ownerCharacterLocalKey");
            if (owner.isTextual() && !owner.asText().isBlank() && !characters.containsKey(owner.asText())) {
                invalid("道具持有人必须引用本次 characters 的 localKey。");
            }
        }
    }

    private void bindExplicitVariants(
        ToolExecutionContext context, String content, String type, String ownerField,
        JsonNode variants, Map<String, ResolvedAsset> owners, List<Binding> bindings
    ) {
        Set<String> localKeys = new HashSet<>();
        for (JsonNode item : variants) {
            if (!localKeys.add(item.path("localKey").asText())) invalid("运行内形态 key 重复。");
            requireEvidence(content, item.path("evidence").asText(), "形态 " + item.path("name").asText());
            ResolvedAsset owner = owners.get(item.path(ownerField).asText());
            if (owner == null) invalid("形态持有人必须引用本次资产 localKey。");
            long variantId = resolveVariant(context, type, owner.id(), item);
            bindings.add(new Binding(type, owner.id(), variantId, item.path("preferred").asBoolean(), null));
        }
    }

    private long resolveVariant(ToolExecutionContext context, String type, long assetId, JsonNode item) {
        String trusted = item.path("variantKey").isTextual() ? item.path("variantKey").asText() : null;
        if (trusted != null && !trusted.isBlank()) {
            long id = parseOpaqueKey(trusted, "v_");
            Integer count = jdbc.queryForObject("""
                select count(*) from asset_visual_variant
                 where id = ? and tenant_id = ? and project_id = ? and asset_type = ?
                   and asset_id = ? and deleted_at is null
                """, Integer.class, id, context.tenantId(), context.projectId(), type, assetId);
            if (count == null || count != 1) invalid("形态 key 不属于对应资产。");
            return id;
        }
        String normalized = AssetIdentityNormalizer.normalize(item.path("name").asText());
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select id, name from asset_visual_variant
             where tenant_id = ? and project_id = ? and asset_type = ? and asset_id = ?
               and deleted_at is null for update
            """, context.tenantId(), context.projectId(), type, assetId);
        List<Map<String, Object>> matches = rows.stream()
            .filter(row -> normalized.equals(AssetIdentityNormalizer.normalize(String.valueOf(row.get("name")))))
            .toList();
        if (matches.size() > 1) {
            throw new BusinessException(ErrorCode.ENTITY_MATCH_AMBIGUOUS,
                "资产形态匹配不唯一，可选安全 key："
                    + matches.stream().map(row -> "v_" + row.get("id")).toList());
        }
        if (!matches.isEmpty()) return ((Number) matches.get(0).get("id")).longValue();
        return insertVariant(context, type, assetId, item.path("name").asText(),
            item.path("description").isNull() ? null : item.path("description").asText(), item);
    }

    private long insertVariant(
        ToolExecutionContext context, String type, long assetId, String name,
        String appearance, JsonNode metadata
    ) {
        Integer existing = jdbc.queryForObject("""
            select count(*) from asset_visual_variant
             where tenant_id = ? and project_id = ? and asset_type = ? and asset_id = ?
               and deleted_at is null
            """, Integer.class, context.tenantId(), context.projectId(), type, assetId);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                insert into asset_visual_variant
                  (tenant_id, project_id, asset_type, asset_id, name, appearance, source_type,
                   generation_status, is_primary, created_by, content_json, generated_by_run_id,
                   created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, 'AI', 'NOT_GENERATED', ?, ?, ?, ?, now(), now())
                """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, context.tenantId());
            statement.setLong(2, context.projectId());
            statement.setString(3, type);
            statement.setLong(4, assetId);
            statement.setString(5, name);
            statement.setString(6, appearance);
            statement.setBoolean(7, existing != null && existing == 0);
            statement.setLong(8, context.userId());
            statement.setString(9, metadata == null ? null : metadata.toString());
            statement.setObject(10, context.agentRunId());
            return statement;
        }, key);
        Number id = generatedId(key);
        if (id == null) throw new IllegalStateException("形态 id 未生成。");
        return id.longValue();
    }

    private void addDefaultBindings(
        ToolExecutionContext context, String type, Map<String, ResolvedAsset> assets, List<Binding> bindings
    ) {
        Set<Long> bound = new HashSet<>();
        bindings.stream().filter(binding -> type.equals(binding.type())).forEach(binding -> bound.add(binding.assetId()));
        for (ResolvedAsset asset : assets.values()) {
            if (bound.contains(asset.id())) continue;
            List<Long> primary = jdbc.queryForList("""
                select id from asset_visual_variant
                 where tenant_id = ? and project_id = ? and asset_type = ? and asset_id = ?
                   and deleted_at is null order by is_primary desc, id limit 1
                """, Long.class, context.tenantId(), context.projectId(), type, asset.id());
            long variantId = primary.isEmpty()
                ? insertVariant(context, type, asset.id(), defaultVariantName(type), null, null)
                : primary.get(0);
            bindings.add(new Binding(type, asset.id(), variantId, true, null));
        }
    }

    private void applySceneUsage(
        JsonNode scenes, Map<String, ResolvedAsset> resolved, List<Binding> bindings, String content
    ) {
        Map<Long, ObjectNode> usage = new HashMap<>();
        for (JsonNode scene : scenes) {
            ResolvedAsset asset = resolved.get(scene.path("localKey").asText());
            ObjectNode value = json.createObjectNode();
            if (scene.path("timeAtmosphere").isTextual()) {
                value.put("timeAtmosphere", scene.path("timeAtmosphere").asText());
            }
            if (scene.path("usageEvidence").isTextual()) {
                requireEvidence(content, scene.path("usageEvidence").asText(), "场景使用信息");
                value.put("evidence", scene.path("usageEvidence").asText());
            }
            if (!value.isEmpty()) usage.put(asset.id(), value);
        }
        for (int i = 0; i < bindings.size(); i++) {
            Binding binding = bindings.get(i);
            if ("SCENE".equals(binding.type()) && usage.containsKey(binding.assetId())) {
                bindings.set(i, new Binding(binding.type(), binding.assetId(), binding.variantId(),
                    binding.preferred(), usage.get(binding.assetId()).toString()));
            }
        }
    }

    private void enforcePreferred(List<Binding> bindings) {
        Map<String, Integer> preferred = new HashMap<>();
        for (Binding binding : bindings) {
            if (!binding.preferred()) continue;
            String owner = binding.type() + ":" + binding.assetId();
            if (preferred.merge(owner, 1, Integer::sum) > 1) {
                invalid("同一资产在一集内只能有一个首选形态。");
            }
        }
    }

    private void replaceBindings(ToolExecutionContext context, List<Binding> bindings) {
        jdbc.update("""
            update asset_visual_variant_episode
               set binding_status = 'RETIRED', retired_at = now(), updated_at = now()
             where tenant_id = ? and project_id = ? and script_id = ? and episode_id = ?
               and generated_by_run_id is not null and retired_at is null
            """, context.tenantId(), context.projectId(), context.scriptId(), context.episodeId());
        for (Binding binding : bindings) {
            Integer active = jdbc.queryForObject("""
                select count(*) from asset_visual_variant_episode
                 where variant_id = ? and episode_id = ? and retired_at is null
                """, Integer.class, binding.variantId(), context.episodeId());
            if (active != null && active > 0) continue;
            jdbc.update("""
                insert into asset_visual_variant_episode
                  (tenant_id, project_id, script_id, episode_id, asset_type, asset_id, variant_id,
                   is_preferred, binding_status, created_by, content_json, generated_by_run_id,
                   created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, now(), now())
                """, context.tenantId(), context.projectId(), context.scriptId(), context.episodeId(),
                binding.type(), binding.assetId(), binding.variantId(), binding.preferred(),
                context.userId(), binding.contentJson(), context.agentRunId());
        }
    }

    private long upsertCoverage(
        ToolExecutionContext context, int schemaVersion, String fingerprint, JsonNode diagnostic
    ) {
        jdbc.update("""
            insert into script_episode_asset_analysis
              (tenant_id, project_id, script_id, episode_id, schema_version, content_fingerprint,
               content_json, generated_by_run_id, created_by, updated_by, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            on duplicate key update schema_version = values(schema_version),
              content_fingerprint = values(content_fingerprint), content_json = values(content_json),
              generated_by_run_id = values(generated_by_run_id), updated_by = values(updated_by),
              updated_at = now()
            """, context.tenantId(), context.projectId(), context.scriptId(), context.episodeId(),
            schemaVersion, fingerprint, diagnostic.toString(), context.agentRunId(),
            context.userId(), context.userId());
        return jdbc.queryForObject("""
            select id from script_episode_asset_analysis where tenant_id = ? and episode_id = ?
            """, Long.class, context.tenantId(), context.episodeId());
    }

    private long parseOpaqueKey(String key, String prefix) {
        if (!key.startsWith(prefix)) invalid("资产 key 类型不匹配。");
        try {
            return Long.parseLong(key.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            invalid("资产 key 格式不正确。");
            return -1;
        }
    }

    private void requireEvidence(String content, String evidence, String label) {
        if (evidence == null || evidence.isBlank() || !content.contains(evidence)) {
            invalid(label + " 的证据必须逐字存在于当前剧集。");
        }
    }

    private void invalid(String message) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private String defaultVariantName(String type) {
        return "SCENE".equals(type) ? "默认场景" : "默认形态";
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Number generatedId(KeyHolder key) {
        if (key.getKeys() != null && key.getKeys().get("id") instanceof Number id) return id;
        return key.getKey();
    }

    private record ReadEpisode(String episodeKey, String content, String fingerprint) {}
    private record ResolvedAsset(long id, String localKey, String type) {}
    private record Binding(String type, long assetId, long variantId, boolean preferred, String contentJson) {}
}
