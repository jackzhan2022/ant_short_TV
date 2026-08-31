package com.antshorttv.review;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public enum ReviewDimension {
    PLOT_LOGIC("剧情逻辑与因果", "script-review-dimension-plot-logic"),
    DIALOGUE("台词合理性", "script-review-dimension-dialogue"),
    CHARACTER_RELATIONSHIP("人物关系一致性", "script-review-dimension-character-relationship"),
    CHARACTER_KNOWLEDGE("人物认知一致性", "script-review-dimension-character-knowledge"),
    CHARACTER_MOTIVATION("人物动机", "script-review-dimension-character-motivation"),
    TIMELINE("时间线连续性", "script-review-dimension-timeline"),
    SCENE_CONTINUITY("场景连续性", "script-review-dimension-scene-continuity"),
    PROP_CONTINUITY("道具连续性", "script-review-dimension-prop-continuity"),
    VISUAL_CONTINUITY("视觉连续性", "script-review-dimension-visual-continuity"),
    SHOOTABILITY("分镜可执行性", "script-review-dimension-shootability"),
    EMOTION("情绪递进", "script-review-dimension-emotion"),
    SUSPENSE_REVERSAL("悬念与反转铺垫", "script-review-dimension-suspense-reversal"),
    FORESHADOWING("伏笔回收", "script-review-dimension-foreshadowing");

    private final String label;
    private final String skillCode;

    ReviewDimension(String label, String skillCode) {
        this.label = label;
        this.skillCode = skillCode;
    }

    public String label() { return label; }
    public String skillCode() { return skillCode; }

    public static List<ReviewDimension> parseAll(List<String> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("至少选择一个审核维度");
        Set<String> requested = new LinkedHashSet<>(values);
        List<ReviewDimension> result = Arrays.stream(values())
            .filter(dimension -> requested.contains(dimension.label) || requested.contains(dimension.name()))
            .toList();
        if (result.size() != requested.size()) throw new IllegalArgumentException("包含未知审核维度");
        return result;
    }

    public static List<String> skillCodes(List<ReviewDimension> dimensions, boolean aggregation) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        result.add("script-review-foundation");
        result.add("script-review-execution-framework");
        dimensions.stream().sorted().map(ReviewDimension::skillCode).forEach(result::add);
        if (aggregation) result.add("script-review-cross-episode-synthesis");
        return List.copyOf(result);
    }
}
