package com.antshorttv.accounting;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.TreeMap;

public final class AiAccountingJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiAccountingJson() {
    }

    public static String write(Map<String, String> dimensions) {
        try {
            return MAPPER.writeValueAsString(new TreeMap<>(dimensions == null ? Map.of() : dimensions));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid pricing dimensions.", exception);
        }
    }

    public static Map<String, String> read(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid stored pricing dimensions.", exception);
        }
    }

    public static String canonicalKey(Map<String, String> dimensions) {
        return new TreeMap<>(dimensions == null ? Map.of() : dimensions).entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce((left, right) -> left + "&" + right)
            .orElse("");
    }
}
