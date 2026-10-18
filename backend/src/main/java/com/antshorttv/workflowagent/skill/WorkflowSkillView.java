package com.antshorttv.workflowagent.skill;

import java.util.List;

public record WorkflowSkillView(
    String code,
    String name,
    String description,
    String content,
    String revision,
    List<String> referencingAgentCodes,
    String scope,
    Long tenantId,
    String tenantName,
    boolean tenantEditable
) {
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public int priority() {
        return "TENANT".equals(scope) ? 200 : 100;
    }

    public WorkflowSkillView(String code, String name, String description, String content,
        String revision, List<String> referencingAgentCodes) {
        this(code, name, description, content, revision, referencingAgentCodes, "SYSTEM", null, null,
            TenantStoryboardSkillPolicy.EDITABLE_CODES.contains(code));
    }
}
