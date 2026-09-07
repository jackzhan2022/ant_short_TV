package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InspirationThumbnailPropertiesTest {

    @Test
    void suppliesSafeThumbnailDefaults() {
        InspirationThumbnailProperties properties = new InspirationThumbnailProperties();

        assertThat(properties.getMaxDimension()).isEqualTo(640);
        assertThat(properties.getJpegQuality()).isEqualTo(0.82f);
        assertThat(properties.getBackfillBatchSize()).isEqualTo(20);
    }
}
