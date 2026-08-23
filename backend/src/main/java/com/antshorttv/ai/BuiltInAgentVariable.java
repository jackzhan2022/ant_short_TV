package com.antshorttv.ai;

public record BuiltInAgentVariable(
    String name,
    String label,
    String type,
    boolean required,
    String description
) {
}
