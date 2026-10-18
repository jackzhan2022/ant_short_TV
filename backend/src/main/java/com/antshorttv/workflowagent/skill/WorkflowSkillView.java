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
    public WorkflowSkillView(String code, String name, String description, String content,
        String revision, List<String> referencingAgentCodes) {
        this(code, name, description, content, revision, referencingAgentCodes, "SYSTEM", null, null,
            TenantStoryboardSkillPolicy.EDITABLE_CODES.contains(code));
    }
}
