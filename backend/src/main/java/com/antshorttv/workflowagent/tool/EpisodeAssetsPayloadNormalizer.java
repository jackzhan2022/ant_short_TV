package com.antshorttv.workflowagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class EpisodeAssetsPayloadNormalizer {
    private static final String[] ARRAY_FIELDS = {
        "characters", "characterLooks", "scenes", "props", "propVariants"
    };

    private EpisodeAssetsPayloadNormalizer() {}

    public static JsonNode normalize(JsonNode input) {
        if (input == null || !input.isObject()) return input;
        ObjectNode normalized = input.deepCopy();
        for (String field : ARRAY_FIELDS) {
            if (!normalized.has(field)) normalized.putArray(field);
        }
        defaultAliases(normalized.withArray("characters"));
        defaultAliases(normalized.withArray("scenes"));
        defaultAliases(normalized.withArray("props"));
        defaultPreferred(normalized.withArray("characterLooks"));
        defaultPreferred(normalized.withArray("propVariants"));
        return normalized;
    }

    private static void defaultAliases(ArrayNode items) {
        items.forEach(item -> {
            if (item instanceof ObjectNode object && !object.has("aliases")) {
                object.putArray("aliases");
            }
        });
    }

    private static void defaultPreferred(ArrayNode items) {
        items.forEach(item -> {
            if (item instanceof ObjectNode object && !object.has("preferred")) {
                object.put("preferred", false);
            }
        });
    }
}
