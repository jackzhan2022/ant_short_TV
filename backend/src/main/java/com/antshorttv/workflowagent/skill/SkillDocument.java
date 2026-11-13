package com.antshorttv.workflowagent.skill;

public record SkillDocument(
    String code,
    String name,
    String description,
    String markdown,
    String content,
    String revision,
    String purpose,
    String promptContract
) {
    public SkillDocument(String code, String name, String description, String markdown, String content, String revision) {
        this(code, name, description, markdown, content, revision, null, "legacy");
    }
}
