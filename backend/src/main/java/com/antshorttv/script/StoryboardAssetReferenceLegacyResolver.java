package com.antshorttv.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StoryboardAssetReferenceLegacyResolver {
    private final JdbcTemplate jdbc;
    private final StoryboardAssetReferenceRepository repository;
    private final ObjectMapper json;

    public StoryboardAssetReferenceLegacyResolver(
        JdbcTemplate jdbc,
        StoryboardAssetReferenceRepository repository,
        ObjectMapper json
    ) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.json = json;
    }

    public LegacyStoryboardReferences resolveStoryboard(
        Long tenantId, Long projectId, Long storyboardId
    ) {
        List<StoryboardAssetReferenceEntity> formal = repository.listActive(
            tenantId, projectId, storyboardId);
        if (!formal.isEmpty()) {
            return new LegacyStoryboardReferences(true, formal.stream()
                .map(row -> new LegacyReference(
                    row.assetType, row.assetId, row.variantId, row.referenceRole, row.sortOrder,
                    row.resolutionStatus, row.sourceName, row.sourceType,
                    Boolean.TRUE.equals(row.lockedByUser)))
                .toList());
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select characters,scene,props from storyboard
             where id=? and tenant_id=? and project_id=? and deleted_at is null
            """, storyboardId, tenantId, projectId);
        if (rows.isEmpty()) return new LegacyStoryboardReferences(false, List.of());
        Map<String, Object> storyboard = rows.get(0);
        List<LegacyReference> result = new ArrayList<>();
        resolveType(result, tenantId, projectId, "CHARACTER", text(storyboard.get("characters")));
        resolveType(result, tenantId, projectId, "SCENE", text(storyboard.get("scene")));
        resolveType(result, tenantId, projectId, "PROP", text(storyboard.get("props")));
        return new LegacyStoryboardReferences(false, List.copyOf(result));
    }

    private void resolveType(
        List<LegacyReference> result,
        Long tenantId,
        Long projectId,
        String assetType,
        String stored
    ) {
        List<String> names = split(stored);
        if (names.isEmpty()) return;
        Map<String, List<AssetIdentity>> identities = catalog(tenantId, projectId, assetType);
        for (int index = 0; index < names.size(); index++) {
            String sourceName = names.get(index);
            List<AssetIdentity> matches = identities.getOrDefault(
                AssetIdentityNormalizer.normalize(sourceName), List.of());
            AssetIdentity match = matches.size() == 1 ? matches.get(0) : null;
            result.add(new LegacyReference(
                assetType,
                match == null ? null : match.id(),
                null,
                role(assetType, index),
                index,
                match == null ? "UNRESOLVED" : "ASSET_PENDING",
                sourceName,
                "LEGACY",
                false
            ));
        }
    }

    private Map<String, List<AssetIdentity>> catalog(
        Long tenantId, Long projectId, String assetType
    ) {
        String table = switch (assetType) {
            case "CHARACTER" -> "character_asset";
            case "SCENE" -> "scene_asset";
            case "PROP" -> "prop_asset";
            default -> throw new IllegalArgumentException("Unsupported asset type " + assetType);
        };
        List<Map<String, Object>> rows = jdbc.queryForList("select id,name,normalized_name,content_json from "
            + table + " where tenant_id=? and project_id=? and deleted_at is null", tenantId, projectId);
        Map<String, List<AssetIdentity>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            AssetIdentity identity = new AssetIdentity(
                ((Number) row.get("id")).longValue(), text(row.get("name")));
            add(result, normalized(row.get("normalized_name"), identity.name()), identity);
            for (String alias : aliases(row.get("content_json"))) add(result, alias, identity);
        }
        return result;
    }

    private void add(
        Map<String, List<AssetIdentity>> catalog, String normalized, AssetIdentity identity
    ) {
        if (normalized.isBlank()) return;
        List<AssetIdentity> values = catalog.computeIfAbsent(normalized, ignored -> new ArrayList<>());
        if (values.stream().noneMatch(item -> item.id().equals(identity.id()))) values.add(identity);
    }

    private List<String> aliases(Object raw) {
        if (raw == null) return List.of();
        try {
            JsonNode aliases = json.readTree(String.valueOf(raw)).path("aliases");
            if (!aliases.isArray()) return List.of();
            List<String> result = new ArrayList<>();
            aliases.forEach(alias -> {
                String value = alias.isTextual() ? alias.asText() : alias.path("name").asText();
                String normalized = AssetIdentityNormalizer.normalize(value);
                if (!normalized.isBlank()) result.add(normalized);
            });
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    static List<String> split(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        return java.util.Arrays.stream(stored.split("[、,，;；]"))
            .map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    private String normalized(Object stored, String fallback) {
        String value = text(stored);
        return value == null || value.isBlank()
            ? AssetIdentityNormalizer.normalize(fallback) : value;
    }

    private String role(String assetType, int index) {
        if ("CHARACTER".equals(assetType) || "PROP".equals(assetType)) return "VISIBLE";
        return index == 0 ? "MAIN" : "SUPPORTING";
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private record AssetIdentity(Long id, String name) {
    }
}

record LegacyStoryboardReferences(boolean formal, List<LegacyReference> references) {
}

record LegacyReference(
    String assetType,
    Long assetId,
    Long variantId,
    String referenceRole,
    Integer sortOrder,
    String resolutionStatus,
    String sourceName,
    String sourceType,
    boolean lockedByUser
) {
}
