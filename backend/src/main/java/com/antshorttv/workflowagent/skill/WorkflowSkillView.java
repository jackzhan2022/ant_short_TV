package com.antshorttv.workflowagent.skill;

import java.util.List;

public record WorkflowSkillView(
    String code,
    String name,
    String description,
    String content,
    String revision,
    List<String> referencingAgentCodes
) {
}
