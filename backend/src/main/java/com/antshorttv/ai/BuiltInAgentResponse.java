package com.antshorttv.ai;

import java.util.List;

public record BuiltInAgentResponse(
    String code,
    String name,
    String description,
    String businessScene,
    String businessSceneName,
    String capability,
    String modelRouting,
    List<BuiltInAgentVariableResponse> variables,
    String outputSchema,
    List<BuiltInSkillSummaryResponse> skills
) {
}
