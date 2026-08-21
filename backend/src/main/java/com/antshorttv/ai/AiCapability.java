package com.antshorttv.ai;

public enum AiCapability {
    TEXT("TEXT"),
    IMAGE("IMAGE"),
    VIDEO_UNDERSTANDING("VIDEO_UNDERSTANDING"),
    VIDEO("VIDEO"),
    AUDIO("AUDIO");

    private final String modelServiceType;

    AiCapability(String modelServiceType) {
        this.modelServiceType = modelServiceType;
    }

    public String modelServiceType() {
        return modelServiceType;
    }
}
