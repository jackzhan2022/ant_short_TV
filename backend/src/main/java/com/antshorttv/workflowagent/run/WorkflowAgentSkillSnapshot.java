package com.antshorttv.workflowagent.run;

public record WorkflowAgentSkillSnapshot(
    String code,
    String name,
    String revision,
    String content
) {
}
