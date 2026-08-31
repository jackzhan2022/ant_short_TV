package com.antshorttv.workflowagent.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkflowToolRunState {
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final List<String> successfulToolCodes = new ArrayList<>();

    public void put(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    public <T> T require(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Missing workflow run state: " + key);
        }
        return type.cast(value);
    }

    public <T> T get(String key, Class<T> type) {
        Object value = attributes.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    public void recordSuccess(String toolCode) {
        successfulToolCodes.add(toolCode);
    }

    public List<String> successfulToolCodes() {
        return Collections.unmodifiableList(successfulToolCodes);
    }

    public void beginSplitFallback(String reason) {
        if ("CHUNK_FALLBACK".equals(attributes.get("splitMode"))) {
            throw new IllegalStateException("Episode splitting fallback already started.");
        }
        attributes.put("splitMode", "CHUNK_FALLBACK");
        attributes.put("splitFallbackReason", reason);
        successfulToolCodes.clear();
    }

    public String splitMode() {
        return String.valueOf(attributes.getOrDefault("splitMode", "FULL"));
    }

    public String splitFallbackReason() {
        Object reason = attributes.get("splitFallbackReason");
        return reason == null ? null : String.valueOf(reason);
    }
}
