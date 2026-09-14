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

/** Validates items against the unchanged tool contract and trusted source text. */
public final class EpisodeAssetsPartialPreparation {
    private static final List<String> FIELDS = List.of("characters", "scenes", "props", "characterLooks", "propVariants");

    private EpisodeAssetsPartialPreparation() {}

    public record Prepared(ObjectNode payload, ArrayNode warnings) {}

    public static Prepared prepare(JsonNode input, JsonNode schema, String content) {
        JsonNode normalized = EpisodeAssetsPayloadNormalizer.normalize(input);
        var validator = new WorkflowToolSchemaValidator();
        if (!(normalized instanceof ObjectNode source)) {
            validator.validate(schema, normalized);
            throw WorkflowToolValidationException.aggregate(List.of("资产结果必须是对象。"));
        }
        ObjectNode result = source.deepCopy();
        FIELDS.forEach(result::putArray);
        // Unknown root fields, scope and schemaVersion remain strict.
        validator.validate(schema, result);
        ArrayNode warnings = result.arrayNode();
        Map<String, String> segments = new HashMap<>();
        new EpisodeSourceSegmenter().segment(content).forEach(s -> segments.put(s.id(), s.text()));
        Map<String, Set<String>> keys = new HashMap<>();
        for (String field : FIELDS) {
            Set<String> seen = new HashSet<>();
            keys.put(field, seen);
            JsonNode items = source.path(field);
            if (!items.isArray()) {
                warn(warnings, "INVALID_CATEGORY", "$." + field, "该类别必须为数组，已保留其他类别。", items);
                continue;
            }
            int limit = schema.path("properties").path(field).path("maxItems").asInt(300);
            for (int i = 0; i < items.size(); i++) {
                String path = "$." + field + "[" + i + "]";
                JsonNode original = items.get(i);
                if (i >= limit || !(original instanceof ObjectNode)) {
                    warn(warnings, "INVALID_ITEM", path, "条目类型错误或超过类别数量限制。", original);
                    continue;
                }
                ObjectNode item = original.deepCopy();
                List<String> errors = new ArrayList<>();
                EpisodeAssetsPayloadNormalizer.resolveEvidence(item, "evidence", segments, path, errors);
                EpisodeAssetsPayloadNormalizer.checkEvidence(item.path("evidence"), content, path + ".evidence", errors);
                if (field.equals("scenes") && (item.hasNonNull("usageEvidence") || item.has("usageEvidenceRef"))) {
                    List<String> usageErrors = new ArrayList<>();
                    EpisodeAssetsPayloadNormalizer.resolveEvidence(item, "usageEvidence", segments, path, usageErrors);
                    EpisodeAssetsPayloadNormalizer.checkEvidence(item.path("usageEvidence"), content, path + ".usageEvidence", usageErrors);
                    if (!usageErrors.isEmpty()) {
                        warn(warnings, "INVALID_EVIDENCE", path + ".usageEvidence", String.join("; ", usageErrors), original);
                        item.remove(List.of("usageEvidence", "usageEvidenceRef", "timeAtmosphere"));
                    }
                }
                if (item.path("aliases").isArray()) {
                    ArrayNode aliases = item.putArray("aliases");
                    int j = 0;
                    for (JsonNode alias : original.path("aliases")) {
                        ObjectNode value = alias.isObject() ? alias.deepCopy() : item.objectNode().put("name", alias.asText()).put("evidence", alias.asText());
                        List<String> aliasErrors = new ArrayList<>();
                        String aliasPath = path + ".aliases[" + j++ + "]";
                        EpisodeAssetsPayloadNormalizer.resolveEvidence(value, "evidence", segments, aliasPath, aliasErrors);
                        EpisodeAssetsPayloadNormalizer.checkEvidence(value.path("evidence"), content, aliasPath, aliasErrors);
                        aliasErrors.addAll(validator.collectErrors(schema.path("properties").path(field).path("items").path("properties").path("aliases").path("items"), value));
                        if (aliasErrors.isEmpty() && aliases.size() < 30) aliases.add(value);
                        else warn(warnings, "INVALID_ALIAS", aliasPath, String.join("; ", aliasErrors), alias);
                    }
                }
                String ownerField = field.equals("characterLooks") ? "characterLocalKey" : field.equals("propVariants") ? "propLocalKey" : null;
                if (ownerField != null && !keys.get(field.equals("characterLooks") ? "characters" : "props").contains(item.path(ownerField).asText())) {
                    errors.add(path + "." + ownerField + " 引用了不存在或无效的 localKey。提交值：" + item.path(ownerField).asText());
                }
                if (field.equals("props") && item.hasNonNull("ownerCharacterLocalKey")
                    && !item.path("ownerCharacterLocalKey").asText().isBlank()
                    && !keys.get("characters").contains(item.path("ownerCharacterLocalKey").asText())) {
                    warn(warnings, "INVALID_OWNER", path + ".ownerCharacterLocalKey", "持有人无效，仅跳过持有关联。", original);
                    item.remove("ownerCharacterLocalKey");
                }
                errors.addAll(validator.collectErrors(schema.path("properties").path(field).path("items"), item));
                String key = item.path("localKey").asText();
                if (key.isBlank() || seen.contains(key)) errors.add(path + ".localKey 为空或重复。");
                if (item.path("name").asText().isBlank()) errors.add(path + ".name 不能为空。");
                if (!errors.isEmpty()) {
                    warn(warnings, "INVALID_ITEM", path, String.join("; ", errors), original);
                } else {
                    seen.add(key);
                    ((ArrayNode) result.path(field)).add(item);
                }
            }
        }
        if (result.path("characters").isEmpty() && result.path("scenes").isEmpty() && result.path("props").isEmpty()) {
            if (!warnings.isEmpty()) {
                throw WorkflowToolValidationException.aggregate(java.util.stream.StreamSupport.stream(
                    warnings.spliterator(), false)
                    .map(w -> w.path("path").asText() + ": " + w.path("message").asText()).toList());
            }
        }
        return new Prepared(result, warnings);
    }

    public static void warn(ArrayNode warnings, String code, String path, String message, JsonNode item) {
        ObjectNode warning = warnings.addObject().put("code", code).put("path", path).put("message", message);
        if (item != null) warning.set("item", item.deepCopy());
    }
}
