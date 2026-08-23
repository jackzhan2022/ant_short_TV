package com.antshorttv.ai;

public record BuiltInSkillSummaryResponse(
    String code,
    String name,
    String category
) {
    static BuiltInSkillSummaryResponse from(BuiltInSkillDefinition skill) {
        return new BuiltInSkillSummaryResponse(skill.code(), skill.name(), skill.category());
    }
}
