package com.antshorttv.ai;

import java.util.List;

public record BuiltInAgentDefinition(
    String code,
    String name,
    String description,
    AiBusinessScene scene,
    AiCapability capability,
    String promptTemplate,
    List<BuiltInAgentVariable> variables,
    String outputSchema,
    List<String> skillCodes
) {
    public BuiltInAgentDefinition {
        variables = List.copyOf(variables);
        skillCodes = List.copyOf(skillCodes);
    }
}
