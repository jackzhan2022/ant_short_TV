package com.antshorttv.ai;

public record BuiltInAgentVariableResponse(
    String name,
    String label,
    String type,
    boolean required,
    String description
) {
    static BuiltInAgentVariableResponse from(BuiltInAgentVariable variable) {
        return new BuiltInAgentVariableResponse(
            variable.name(),
            variable.label(),
            variable.type(),
            variable.required(),
            variable.description()
        );
    }
}
