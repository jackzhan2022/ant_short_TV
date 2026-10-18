package com.antshorttv.workflowagent.run;

public record WorkflowAgentSkillSnapshot(
    String code,
    String name,
    String revision,
    String content,
    String scope,
    Long tenantId
) {
    public WorkflowAgentSkillSnapshot {
        scope = scope == null ? "SYSTEM" : scope;
    }

    public WorkflowAgentSkillSnapshot(String code, String name, String revision, String content) {
        this(code, name, revision, content, "SYSTEM", null);
    }
}
