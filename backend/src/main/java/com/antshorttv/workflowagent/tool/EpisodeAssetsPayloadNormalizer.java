package com.antshorttv.workflowagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EpisodeAssetsPayloadNormalizer {
    private static final String[] ARRAY_FIELDS = {
        "characters", "characterLooks", "scenes", "props", "propVariants"
    };
    // Only remove schema vocabulary that has no business meaning in this contract.
    private static final List<String> SCHEMA_FIELDS = List.of(
        "$schema", "maxItems", "minItems", "maxLength", "minLength", "additionalProperties");

    private EpisodeAssetsPayloadNormalizer() {}

    public static JsonNode prepare(JsonNode input, JsonNode schema, String content) {
        JsonNode normalized = normalize(input);
        List<String> errors = new ArrayList<>();
        Map<String, String> segments = new HashMap<>();
        if (content != null) {
            new EpisodeSourceSegmenter().segment(content).forEach(segment -> segments.put(segment.id(), segment.text()));
        }
        for (String field : ARRAY_FIELDS) {
            JsonNode items = normalized == null ? null : normalized.path(field);
            if (items == null || !items.isArray()) continue;
            for (int i = 0; i < items.size(); i++) {
                if (!(items.get(i) instanceof ObjectNode item)) continue;
                String path = "$." + field + "[" + i + "]";
                resolveEvidence(item, "evidence", segments, path, errors);
                if (field.equals("scenes")) resolveEvidence(item, "usageEvidence", segments, path, errors);
                if (item.path("aliases") instanceof ArrayNode aliases) {
                    for (int j = 0; j < aliases.size(); j++) {
                        JsonNode alias = aliases.get(j);
                        if (alias.isTextual() && content != null && !alias.asText().isBlank()
                            && content.contains(alias.asText())) {
                            ObjectNode replacement = item.objectNode();
                            replacement.put("name", alias.asText());
                            replacement.put("evidence", alias.asText());
                            aliases.set(j, replacement);
                        } else if (alias instanceof ObjectNode object) {
                            resolveEvidence(object, "evidence", segments, path + ".aliases[" + j + "]", errors);
                        }
                    }
                }
            }
        }
        errors.addAll(new WorkflowToolSchemaValidator().collectErrors(schema, normalized));
        if (content != null && normalized != null && normalized.isObject()) {
            errors.addAll(semanticErrors(normalized, content));
        }
        if (!errors.isEmpty()) {
            throw WorkflowToolValidationException.aggregate(errors);
        }
        return normalized;
    }

    public static JsonNode normalize(JsonNode input) {
        if (input == null || !input.isObject()) return input;
        ObjectNode normalized = input.deepCopy();
        removeSchemaFields(normalized);
        for (String field : ARRAY_FIELDS) {
            if (!normalized.has(field)) normalized.putArray(field);
            JsonNode items = normalized.path(field);
            if (!items.isArray()) continue; // Let schema validation report the original field type.
            for (JsonNode item : items) {
                if (!(item instanceof ObjectNode object)) continue;
                if (field.equals("characterLooks") || field.equals("propVariants")) {
                    if (!object.has("preferred")) object.put("preferred", false);
                } else if (!object.has("aliases")) {
                    object.putArray("aliases");
                }
            }
        }
        return normalized;
    }

    private static void removeSchemaFields(JsonNode node) {
        if (node instanceof ObjectNode object) object.remove(SCHEMA_FIELDS);
        if (node != null && node.isContainerNode()) node.forEach(EpisodeAssetsPayloadNormalizer::removeSchemaFields);
    }

    private static void resolveEvidence(ObjectNode item, String field, Map<String, String> segments,
                                        String path, List<String> errors) {
        String refField = field + "Ref";
        if (!item.has(refField)) return;
        JsonNode ref = item.path(refField);
        String text = segments.get(ref.path("segmentId").asText());
        if (!ref.isObject() || text == null) {
            errors.add(path + "." + refField + " 必须引用当前剧集有效的 segmentId。");
            return;
        }
        int start = 0;
        int end = text.length();
        if (ref.has("start") || ref.has("end")) {
            if (!ref.path("start").isIntegralNumber() || !ref.path("start").canConvertToInt()
                || !ref.path("end").isIntegralNumber() || !ref.path("end").canConvertToInt()) {
                errors.add(path + "." + refField + " 的 start/end 必须成对提供整数。");
                return;
            }
            start = ref.path("start").intValue();
            end = ref.path("end").intValue();
        }
        if (start < 0 || end <= start || end > text.length() || end - start > 1000
            || splitsSurrogate(text, start) || splitsSurrogate(text, end)) {
            errors.add(path + "." + refField + " 范围无效；须为片段内非空、最多1000字符的完整原文。");
            return;
        }
        item.put(field, text.substring(start, end));
    }

    private static boolean splitsSurrogate(String text, int offset) {
        return offset > 0 && offset < text.length()
            && Character.isHighSurrogate(text.charAt(offset - 1)) && Character.isLowSurrogate(text.charAt(offset));
    }

    private static List<String> semanticErrors(JsonNode payload, String content) {
        List<String> errors = new ArrayList<>();
        Map<String, Set<String>> keys = new HashMap<>();
        for (String field : ARRAY_FIELDS) {
            Set<String> seen = new HashSet<>();
            keys.put(field, seen);
            JsonNode items = payload.path(field);
            if (!items.isArray()) continue;
            for (int i = 0; i < items.size(); i++) {
                JsonNode item = items.get(i);
                if (!item.isObject()) continue;
                String path = "$." + field + "[" + i + "]";
                String key = item.path("localKey").asText();
                if (key.isBlank() || !seen.add(key)) errors.add(path + ".localKey 不能为空或重复。");
                if (item.path("name").asText().isBlank()) errors.add(path + ".name 不能为空。");
                checkEvidence(item.path("evidence"), content, path + ".evidence", errors);
                JsonNode aliases = item.path("aliases");
                if (aliases.isArray()) {
                    for (int j = 0; j < aliases.size(); j++) {
                        if (aliases.get(j).isObject()) {
                            checkEvidence(aliases.get(j).path("evidence"), content, path + ".aliases[" + j + "].evidence", errors);
                        }
                    }
                }
                if (field.equals("scenes") && item.hasNonNull("usageEvidence")) {
                    checkEvidence(item.path("usageEvidence"), content, path + ".usageEvidence", errors);
                }
            }
        }
        checkOwners(payload.path("props"), "props", "ownerCharacterLocalKey", keys.get("characters"), false, errors);
        checkOwners(payload.path("characterLooks"), "characterLooks", "characterLocalKey", keys.get("characters"), true, errors);
        checkOwners(payload.path("propVariants"), "propVariants", "propLocalKey", keys.get("props"), true, errors);
        return errors;
    }

    private static void checkEvidence(JsonNode evidence, String content, String path, List<String> errors) {
        if (!evidence.isTextual() || evidence.asText().isBlank() || !content.contains(evidence.asText())) {
            errors.add(path + " 的证据必须逐字存在于当前剧集；可改用对应 evidenceRef/usageEvidenceRef 引用原文片段。");
        }
    }

    private static void checkOwners(JsonNode items, String field, String ownerField, Set<String> keys,
                                    boolean required, List<String> errors) {
        if (!items.isArray()) return;
        Map<String, Integer> preferred = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            if (!item.isObject()) continue;
            String owner = item.path(ownerField).asText("");
            String path = "$." + field + "[" + i + "]";
            if ((required || !owner.isBlank()) && !keys.contains(owner)) {
                errors.add(path + "." + ownerField + " 必须引用本次资产 localKey。");
            }
            if (required && item.path("preferred").asBoolean(false)) {
                Integer first = preferred.putIfAbsent(owner, i);
                if (first != null) errors.add(path + ".preferred 与 $." + field + "[" + first
                    + "].preferred 冲突：同一资产在一集内只能有一个首选形态。");
            }
        }
    }
}
