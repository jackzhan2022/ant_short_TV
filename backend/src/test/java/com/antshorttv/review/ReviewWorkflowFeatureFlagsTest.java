package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReviewWorkflowFeatureFlagsTest {

    @Test
    void exposesEachRolloutCapabilityIndependently() {
        ReviewWorkflowFeatureFlags flags = new ReviewWorkflowFeatureFlags(true, false, true, false);

        assertThat(flags.cacheObservability()).isTrue();
        assertThat(flags.dimensionalOrchestration()).isFalse();
        assertThat(flags.semanticReview()).isTrue();
        assertThat(flags.anomalyGate()).isFalse();
    }
}
