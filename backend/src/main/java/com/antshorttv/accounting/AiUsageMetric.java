package com.antshorttv.accounting;

public enum AiUsageMetric {
    CALL("CALL"),
    INPUT_TOKEN("TOKEN"),
    OUTPUT_TOKEN("TOKEN"),
    IMAGE("IMAGE"),
    VIDEO_SECOND("SECOND"),
    AUDIO_SECOND("SECOND"),
    CHARACTER("CHARACTER");

    private final String unit;

    AiUsageMetric(String unit) {
        this.unit = unit;
    }

    public String unit() {
        return unit;
    }
}
