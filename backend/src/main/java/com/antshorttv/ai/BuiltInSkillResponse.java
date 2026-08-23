package com.antshorttv.ai;

import java.util.List;

public record BuiltInSkillResponse(
    String code,
    String name,
    String description,
    String category,
    String content,
    List<BuiltInAgentSummaryResponse> agents
) {
}
