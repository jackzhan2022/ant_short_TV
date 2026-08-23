package com.antshorttv.ai;

public record BuiltInAgentSummaryResponse(
    String code,
    String name,
    String businessScene
) {
    static BuiltInAgentSummaryResponse from(BuiltInAgentDefinition agent) {
        return new BuiltInAgentSummaryResponse(agent.code(), agent.name(), agent.scene().code());
    }
}
