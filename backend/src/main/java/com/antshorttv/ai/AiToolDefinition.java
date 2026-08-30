package com.antshorttv.ai;

import java.util.Map;

public record AiToolDefinition(String code, String description, Map<String, Object> inputSchema) {
    public AiToolDefinition {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }
}
