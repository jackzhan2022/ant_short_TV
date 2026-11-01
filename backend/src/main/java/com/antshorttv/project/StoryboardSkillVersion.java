package com.antshorttv.project;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.math.BigDecimal;

public enum StoryboardSkillVersion {
    V1("short-drama-storyboard-production"),
    V2("short-drama-storyboard-production-v2");

    private final String skillCode;

    StoryboardSkillVersion(String skillCode) { this.skillCode = skillCode; }

    public String skillCode() { return skillCode; }

    public int minimumShots() { return this == V2 ? 1 : 2; }
    public BigDecimal minimumBoardDuration() { return this == V2 ? new BigDecimal("4") : BigDecimal.TEN; }
    public BigDecimal maximumShotDuration() { return this == V2 ? new BigDecimal("6.5") : new BigDecimal("4"); }

    public static StoryboardSkillVersion resolve(String value) {
        if (value == null || value.isBlank()) return V1;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择原分镜或分镜技能2.0。");
        }
    }
}
