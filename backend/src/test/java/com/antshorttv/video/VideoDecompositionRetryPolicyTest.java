package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VideoDecompositionRetryPolicyTest {
    @Test
    void allowsOnlyRetryableFailedEpisodesWithoutAResultOrActiveClaim() {
        assertThat(VideoDecompositionRetryPolicy.allows("FAILED", true, null, false)).isTrue();
        for (String status : new String[] {
            "PENDING_ANALYSIS", "ANALYZING", "SUCCEEDED", "PENDING_REVIEW", "CONFIRMED"
        }) {
            assertThat(VideoDecompositionRetryPolicy.allows(status, true, null, false))
                .as(status).isFalse();
        }
        assertThat(VideoDecompositionRetryPolicy.allows("FAILED", false, null, false)).isFalse();
        assertThat(VideoDecompositionRetryPolicy.allows("FAILED", true, "claim", false)).isFalse();
        assertThat(VideoDecompositionRetryPolicy.allows("FAILED", true, null, true)).isFalse();
    }
}
