package com.antshorttv.workflowagent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkflowToolRegistry {
    private final Map<String, WorkflowToolDefinition> definitions;

    public WorkflowToolRegistry(List<WorkflowToolDefinition> definitions) {
        Map<String, WorkflowToolDefinition> indexed = new LinkedHashMap<>();
        definitions.stream().sorted(java.util.Comparator.comparing(WorkflowToolDefinition::code))
            .forEach(definition -> {
                if (indexed.putIfAbsent(definition.code(), definition) != null) {
                    throw new IllegalStateException("Duplicate workflow tool: " + definition.code());
                }
            });
        this.definitions = Map.copyOf(indexed);
    }

    public boolean contains(String code) {
        return definitions.containsKey(code);
    }

    public WorkflowToolDefinition require(String code) {
        WorkflowToolDefinition definition = definitions.get(code);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown workflow tool: " + code);
        }
        return definition;
    }

    public List<WorkflowToolMetadata> catalog() {
        return definitions.values().stream().map(WorkflowToolDefinition::metadata)
            .sorted(java.util.Comparator.comparing(WorkflowToolMetadata::code)).toList();
    }
}
