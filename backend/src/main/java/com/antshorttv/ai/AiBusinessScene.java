package com.antshorttv.ai;

public enum AiBusinessScene {
    SCRIPT_GENERATE("script_generate", "AI生成剧本", AiCapability.TEXT, null),
    SCRIPT_REWRITE("script_rewrite", "AI改写剧本", AiCapability.TEXT, null),
    CHARACTER_EXTRACT("character_extract", "AI提取角色", AiCapability.TEXT, "script.element.character.extract"),
    SCENE_EXTRACT("scene_extract", "AI提取场景", AiCapability.TEXT, "script.element.scene.extract"),
    PROP_EXTRACT("prop_extract", "AI提取道具", AiCapability.TEXT, "script.element.prop.extract"),
    STORYBOARD_BREAKDOWN("storyboard_breakdown", "AI拆解分镜", AiCapability.TEXT, null),
    PROMPT_GENERATE("prompt_generate", "AI生成提示词", AiCapability.TEXT, null),
    VIDEO_UNDERSTANDING("video_understanding", "视频理解", AiCapability.VIDEO_UNDERSTANDING, "video.understanding.analysis"),
    VIDEO_SCRIPT_DRAFT("video_script_draft", "视频拆剧草稿生成", AiCapability.TEXT, "video.script.draft");

    private final String code;
    private final String displayName;
    private final AiCapability capability;
    private final String promptTemplateId;

    AiBusinessScene(String code, String displayName, AiCapability capability, String promptTemplateId) {
        this.code = code;
        this.displayName = displayName;
        this.capability = capability;
        this.promptTemplateId = promptTemplateId;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public AiCapability capability() {
        return capability;
    }

    public String promptTemplateId() {
        return promptTemplateId;
    }

    public String pointScene() {
        return code;
    }
}
