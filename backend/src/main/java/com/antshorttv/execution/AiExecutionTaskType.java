package com.antshorttv.execution;

import java.util.Locale;

public enum AiExecutionTaskType {
    TEXT, IMAGE, VIDEO;

    public static AiExecutionTaskType classify(String capability, String scene, String businessType) {
        String value = capability == null ? "" : capability.toUpperCase(Locale.ROOT);
        if (value.contains("UNDERSTANDING") || value.contains("ANALYSIS")) return TEXT;
        if (value.startsWith("IMAGE") || "ai_image_generate".equals(scene)
            || "AI_IMAGE".equals(businessType) || "AI_IMAGE_TASK".equals(businessType)) return IMAGE;
        if (value.equals("VIDEO") || value.equals("VIDEO_GENERATION")
            || "AI_VIDEO_TASK".equals(businessType) || "AI_VIDEO".equals(businessType)
            || "ai_video_generate".equals(scene)) return VIDEO;
        return TEXT;
    }
}
