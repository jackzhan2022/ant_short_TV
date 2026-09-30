package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageDisplayRenditionPlannerTest {
    private final ImageDisplayRenditionPlanner planner = new ImageDisplayRenditionPlanner(
        new ObjectStorageKeyFactory()
    );

    @Test
    void appliesImageSlimOnceWithoutResizingSupportedFormats() {
        assertPlan("image/png", "display.png", "image/png", "imageSlim");
        assertPlan("image/jpeg", "display.jpg", "image/jpeg", "imageSlim");
        assertPlan("image/gif", "display.gif", "image/gif", "imageSlim");
    }

    @Test
    void convertsUnsupportedWebpToPngBeforeImageSlim() {
        ImageDisplayRenditionPlan plan = planner.plan(
            "materials/11/22/images/202609/44/v1/original.webp", "image/webp"
        );

        assertThat(plan.objectKey()).endsWith("/derived/display.png");
        assertThat(plan.mimeType()).isEqualTo("image/png");
        assertThat(plan.processRule()).isEqualTo("imageMogr2/format/png|imageSlim");
        assertThat(plan.processRule()).doesNotContain("thumbnail", "resize");
    }

    private void assertPlan(
        String sourceMimeType,
        String outputSuffix,
        String outputMimeType,
        String processRule
    ) {
        ImageDisplayRenditionPlan plan = planner.plan(
            "materials/11/22/images/202609/44/v1/original.png", sourceMimeType
        );
        assertThat(plan.objectKey()).endsWith(outputSuffix);
        assertThat(plan.mimeType()).isEqualTo(outputMimeType);
        assertThat(plan.processRule()).isEqualTo(processRule);
        assertThat(plan.processRule()).doesNotContain("thumbnail", "resize");
    }
}
