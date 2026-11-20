package com.antshorttv.project;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.math.BigDecimal;

public enum StoryboardSkillVersion {
    V1("short-drama-storyboard-production"),
    V2("short-drama-storyboard-production-v2"),
    V3("short-drama-storyboard-production-v3");

    private final String skillCode;

    StoryboardSkillVersion(String skillCode) { this.skillCode = skillCode; }

    public String skillCode() { return skillCode; }

    public int minimumShots() { return this == V1 ? 2 : 1; }
    public BigDecimal minimumBoardDuration() {
        return switch (this) {
            case V1 -> BigDecimal.TEN;
            case V2 -> new BigDecimal("4");
            case V3 -> new BigDecimal("15");
        };
    }
    public BigDecimal maximumShotDuration() {
        return switch (this) {
            case V1 -> new BigDecimal("4");
            case V2 -> new BigDecimal("6.5");
            case V3 -> new BigDecimal("15");
        };
    }

    public static StoryboardSkillVersion resolve(String value) {
        if (value == null || value.isBlank()) return V1;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择原分镜、分镜技能2.0或分镜技能3.0。");
        }
    }
}
