package com.antshorttv.ai;

public enum AiBusinessScene {
    SCRIPT_GENERATE("script_generate", "AI生成剧本", AiCapability.TEXT, null),
    SCRIPT_REWRITE("script_rewrite", "AI改写剧本", AiCapability.TEXT, null, "script-rewrite"),
    SCRIPT_GLOBAL_UNDERSTANDING("script_global_understanding", "剧情全局理解", AiCapability.TEXT, null, "script-global-understanding"),
    SCRIPT_EPISODE_SPLIT("script_episode_split", "剧集智能拆分", AiCapability.TEXT, null, "script-episode-split"),
    SCRIPT_EPISODE_SUMMARY("script_episode_summary", "剧集概要提炼", AiCapability.TEXT, null, "script-episode-summary"),
    SCRIPT_CHARACTER_SCENE_RECOGNITION("script_character_scene_recognition", "角色场景识别", AiCapability.TEXT, null, "script-character-scene-recognition"),
    CHARACTER_EXTRACT("character_extract", "AI提取角色", AiCapability.TEXT, "script.element.character.extract", "script-character-extract"),
    SCENE_EXTRACT("scene_extract", "AI提取场景", AiCapability.TEXT, "script.element.scene.extract", "script-scene-extract"),
    PROP_EXTRACT("prop_extract", "AI提取道具", AiCapability.TEXT, "script.element.prop.extract", "script-prop-extract"),
    STORYBOARD_BREAKDOWN("storyboard_breakdown", "AI拆解分镜", AiCapability.TEXT, null),
    PROMPT_GENERATE("prompt_generate", "AI生成提示词", AiCapability.TEXT, null),
    VIDEO_UNDERSTANDING("video_understanding", "视频理解", AiCapability.VIDEO_UNDERSTANDING, "video.understanding.analysis", "video-understanding"),
    VIDEO_SCRIPT_DRAFT("video_script_draft", "视频拆剧草稿生成", AiCapability.TEXT, "video.script.draft", "video-script-draft"),
    SCRIPT_REVIEW("script_review", "剧本审核", AiCapability.TEXT, null, "script-review");

    private final String code;
    private final String displayName;
    private final AiCapability capability;
    private final String promptTemplateId;
    private final String agentCode;

    AiBusinessScene(String code, String displayName, AiCapability capability, String promptTemplateId) {
        this(code, displayName, capability, promptTemplateId, null);
    }

    AiBusinessScene(String code, String displayName, AiCapability capability, String promptTemplateId, String agentCode) {
        this.code = code;
        this.displayName = displayName;
        this.capability = capability;
        this.promptTemplateId = promptTemplateId;
        this.agentCode = agentCode;
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

    public String agentCode() {
        return agentCode;
    }

    public String pointScene() {
        return code;
    }
}
