package com.antshorttv.workflowagent.skill;

public record SkillDocument(
    String code,
    String name,
    String description,
    String markdown,
    String content,
    String revision
) {
}
