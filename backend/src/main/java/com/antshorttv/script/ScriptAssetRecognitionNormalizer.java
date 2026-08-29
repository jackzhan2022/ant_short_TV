package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ScriptAssetRecognitionNormalizer {
    private static final Set<String> COLLECTION_FIELDS = Set.of(
        "characters", "scenes", "props", "locations", "key_items");
    private static final Set<String> WRAPPERS = Set.of("assets", "short_drama_assets", "data", "result", "output");
    private static final int MAX_CANDIDATES_PER_TYPE = 500;
    private final ObjectMapper objectMapper;

    public ScriptAssetRecognitionNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NormalizedRecognition normalize(String rawResponse) {
        ObjectNode root = unwrap(parseObject(rawResponse));
        List<String> globalErrors = validateTopLevel(root);
        List<NormalizedCandidate> candidates = new ArrayList<>();
        appendCandidates(candidates, root.get("characters"), AssetType.CHARACTER, "characters");
        appendCandidates(candidates, firstPresent(root, "scenes", "locations"), AssetType.SCENE, "scenes");
        appendCandidates(candidates, firstPresent(root, "props", "key_items"), AssetType.PROP, "props");
        candidates = mergeDuplicateCandidates(candidates);
        boolean valid = globalErrors.isEmpty()
            && candidates.stream().allMatch(candidate -> candidate.validationErrors().isEmpty());
        ObjectNode normalized = objectMapper.createObjectNode();
        ArrayNode characters = normalized.putArray("characters");
        ArrayNode scenes = normalized.putArray("scenes");
        ArrayNode props = normalized.putArray("props");
        candidates.forEach(candidate -> {
            switch (candidate.assetType()) {
                case CHARACTER -> characters.add(candidate.data());
                case SCENE -> scenes.add(candidate.data());
                case PROP -> props.add(candidate.data());
            }
        });
        return new NormalizedRecognition(valid, List.copyOf(globalErrors), List.copyOf(candidates), normalized.toString());
    }

    public NormalizedRecognition normalizePartial(String rawResponse) {
        ObjectNode root = unwrap(parseObject(rawResponse)).deepCopy();
        if (!root.has("characters")) {
            root.putArray("characters");
        }
        if (!root.has("scenes") && !root.has("locations")) {
            root.putArray("scenes");
        }
        if (!root.has("props") && !root.has("key_items")) {
            root.putArray("props");
        }
        return normalize(root.toString());
    }

    private ObjectNode parseObject(String rawResponse) {
        String value = rawResponse == null ? "" : rawResponse.trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            value = value.substring(start, end + 1);
        }
        try {
            JsonNode parsed = objectMapper.readTree(value);
            if (parsed != null && parsed.isObject()) {
                return (ObjectNode) parsed;
            }
        } catch (Exception ignored) {
            // Converted to a stable business error below.
        }
        throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "角色场景识别结果必须是有效 JSON 对象。");
    }

    private ObjectNode unwrap(ObjectNode source) {
        ObjectNode current = source;
        for (int depth = 0; depth < 3; depth++) {
            ObjectNode next = null;
            for (String wrapper : WRAPPERS) {
                JsonNode value = current.get(wrapper);
                if (value != null && value.isObject()) {
                    next = (ObjectNode) value;
                    break;
                }
            }
            if (next == null) {
                return current;
            }
            current = next;
        }
        return current;
    }

    private List<String> validateTopLevel(ObjectNode root) {
        List<String> errors = new ArrayList<>();
        Iterator<String> fields = root.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!COLLECTION_FIELDS.contains(field)) {
                errors.add("unsupported top-level field: " + field);
            }
        }
        for (String required : List.of("characters", "scenes", "props")) {
            JsonNode value = firstPresent(root, required, legacyCollection(required));
            if (value == null) {
                errors.add("missing collection: " + required);
            } else if (!value.isArray()) {
                errors.add(required + " must be an array");
            } else if (value.size() > MAX_CANDIDATES_PER_TYPE) {
                errors.add(required + " exceeds " + MAX_CANDIDATES_PER_TYPE + " items");
            }
        }
        return errors;
    }

    private String legacyCollection(String field) {
        return switch (field) {
            case "scenes" -> "locations";
            case "props" -> "key_items";
            default -> field;
        };
    }

    private JsonNode firstPresent(ObjectNode root, String primary, String fallback) {
        return root.has(primary) ? root.get(primary) : root.get(fallback);
    }

    private void appendCandidates(
        List<NormalizedCandidate> target,
        JsonNode collection,
        AssetType assetType,
        String sourceKey
    ) {
        if (collection == null || !collection.isArray()) {
            return;
        }
        int index = 0;
        for (JsonNode source : collection) {
            List<String> errors = new ArrayList<>();
            ObjectNode data = adaptCandidate(source, assetType, errors);
            validateCandidateFields(data, assetType, errors);
            String name = textual(data.get("name"));
            if (name == null) {
                errors.add("name is required");
            } else if (name.length() > 100) {
                errors.add("name exceeds 100 characters");
            }
            validateStringFields(data, errors);
            List<String> aliases = aliases(data, errors);
            data.remove("aliases");
            String normalizedName = normalizeName(name);
            String groupKey = normalizedName == null
                ? assetType + ":invalid:" + index
                : assetType + ":" + normalizedName;
            target.add(new NormalizedCandidate(
                assetType,
                index,
                sourceKey,
                name,
                normalizedName,
                data,
                aliases,
                List.copyOf(new LinkedHashSet<>(errors)),
                groupKey,
                errors.isEmpty() ? "PENDING_REVIEW" : "INVALID",
                Map.of("normalization", "exact", "scope", assetType.name())
            ));
            index++;
        }
    }

    private ObjectNode adaptCandidate(JsonNode source, AssetType assetType, List<String> errors) {
        ObjectNode data;
        if (source != null && source.isTextual()) {
            data = objectMapper.createObjectNode().put("name", source.asText().trim());
        } else if (source != null && source.isObject()) {
            data = ((ObjectNode) source).deepCopy();
        } else {
            data = objectMapper.createObjectNode();
            errors.add("candidate must be a string or object");
        }
        copyAlias(data, "role_type", "roleType");
        copyAlias(data, "scene_type", "sceneType");
        copyAlias(data, "prop_type", "propType");
        copyAlias(data, "age_range", "ageRange");
        copyAlias(data, "visual_style", "visualStyle");
        copyAlias(data, "plot_function", "plotFunction");
        copyAlias(data, "related_character", "relatedCharacter");
        switch (assetType) {
            case CHARACTER -> putDefault(data, "roleType", "SUPPORTING");
            case SCENE -> putDefault(data, "sceneType", "INTERIOR");
            case PROP -> putDefault(data, "propType", "KEY_PROP");
        }
        return data;
    }

    private void copyAlias(ObjectNode data, String source, String target) {
        if (!data.has(target) && data.has(source)) {
            data.set(target, data.get(source));
        }
        data.remove(source);
    }

    private void putDefault(ObjectNode data, String field, String value) {
        if (textual(data.get(field)) == null) {
            data.put(field, value);
        }
    }

    private void validateStringFields(ObjectNode data, List<String> errors) {
        data.fields().forEachRemaining(entry -> {
            if (Set.of("aliases", "personality").contains(entry.getKey())) {
                return;
            }
            JsonNode value = entry.getValue();
            if (!value.isNull() && !value.isTextual()) {
                errors.add(entry.getKey() + " must be a string");
            } else if (value.isTextual()) {
                int limit = "prompt".equals(entry.getKey()) || "description".equals(entry.getKey()) ? 4000 : 500;
                if (value.asText().length() > limit && !"name".equals(entry.getKey())) {
                    errors.add(entry.getKey() + " exceeds " + limit + " characters");
                }
            }
        });
    }

    private void validateCandidateFields(ObjectNode data, AssetType assetType, List<String> errors) {
        Set<String> allowed = switch (assetType) {
            case CHARACTER -> Set.of("name", "aliases", "alias", "roleType", "gender", "ageRange",
                "identity", "personality", "appearance", "prompt");
            case SCENE -> Set.of("name", "aliases", "alias", "sceneType", "atmosphere", "description",
                "visualStyle", "prompt");
            case PROP -> Set.of("name", "aliases", "alias", "propType", "appearance", "plotFunction",
                "relatedCharacter", "prompt");
        };
        data.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) errors.add("unsupported field: " + field);
        });
        validateEnum(data, errors, "roleType", Set.of("LEAD", "SUPPORTING", "MINOR", "OTHER"));
        validateEnum(data, errors, "sceneType", Set.of("INTERIOR", "EXTERIOR", "MIXED", "OTHER"));
        validateEnum(data, errors, "propType", Set.of("KEY_PROP", "DAILY", "WEAPON", "DOCUMENT", "OTHER"));
        JsonNode personality = data.get("personality");
        if (personality != null && !personality.isNull()
            && (!personality.isArray() || !allTextual(personality))) {
            errors.add("personality must contain strings");
        }
    }

    private void validateEnum(ObjectNode data, List<String> errors, String field, Set<String> allowed) {
        JsonNode value = data.get(field);
        if (value != null && !value.isNull() && value.isTextual()
            && !allowed.contains(value.asText().trim().toUpperCase(Locale.ROOT))) {
            errors.add(field + " has unsupported value");
        }
    }

    private boolean allTextual(JsonNode array) {
        for (JsonNode value : array) if (!value.isTextual()) return false;
        return true;
    }

    private List<NormalizedCandidate> mergeDuplicateCandidates(List<NormalizedCandidate> candidates) {
        Map<String, NormalizedCandidate> merged = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (NormalizedCandidate candidate : candidates) {
            NormalizedCandidate current = merged.get(candidate.duplicateGroupKey());
            if (current == null || candidate.normalizedName() == null) {
                merged.put(candidate.duplicateGroupKey(), candidate);
                counts.put(candidate.duplicateGroupKey(), 1);
                continue;
            }
            candidate.data().fields().forEachRemaining(entry -> {
                JsonNode existing = current.data().get(entry.getKey());
                if (existing == null || existing.isNull() || (existing.isTextual() && existing.asText().isBlank())) {
                    current.data().set(entry.getKey(), entry.getValue());
                }
            });
            LinkedHashSet<String> aliases = new LinkedHashSet<>(current.aliases());
            aliases.addAll(candidate.aliases());
            if (!candidate.name().equals(current.name())) aliases.add(candidate.name());
            LinkedHashSet<String> errors = new LinkedHashSet<>(current.validationErrors());
            errors.addAll(candidate.validationErrors());
            int count = counts.merge(candidate.duplicateGroupKey(), 1, Integer::sum);
            merged.put(candidate.duplicateGroupKey(), new NormalizedCandidate(
                current.assetType(), current.sourceIndex(), current.sourceKey(), current.name(),
                current.normalizedName(), current.data(), List.copyOf(aliases), List.copyOf(errors),
                current.duplicateGroupKey(), errors.isEmpty() ? "PENDING_REVIEW" : "INVALID",
                Map.of("normalization", "exact", "scope", current.assetType().name(),
                    "duplicatesMerged", String.valueOf(count))));
        }
        return List.copyOf(merged.values());
    }

    private List<String> aliases(ObjectNode data, List<String> errors) {
        JsonNode aliases = data.has("aliases") ? data.get("aliases") : data.get("alias");
        data.remove("alias");
        if (aliases == null || aliases.isNull()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (aliases.isTextual()) {
            addAlias(result, aliases.asText());
        } else if (aliases.isArray()) {
            aliases.forEach(value -> {
                if (value.isTextual()) {
                    addAlias(result, value.asText());
                } else {
                    errors.add("aliases must contain strings");
                }
            });
        } else {
            errors.add("aliases must be a string or string array");
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }

    private void addAlias(List<String> result, String alias) {
        String trimmed = alias == null ? "" : alias.trim();
        if (!trimmed.isBlank() && trimmed.length() <= 100) {
            result.add(trimmed);
        }
    }

    private String textual(JsonNode value) {
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}\\s]+", "");
        return normalized.isBlank() ? null : normalized;
    }

    public record NormalizedRecognition(
        boolean valid,
        List<String> globalErrors,
        List<NormalizedCandidate> candidates,
        String normalizedJson
    ) {}

    public record NormalizedCandidate(
        AssetType assetType,
        int sourceIndex,
        String sourceKey,
        String name,
        String normalizedName,
        ObjectNode data,
        List<String> aliases,
        List<String> validationErrors,
        String duplicateGroupKey,
        String reviewStatus,
        Map<String, String> matchEvidence
    ) {}
}
