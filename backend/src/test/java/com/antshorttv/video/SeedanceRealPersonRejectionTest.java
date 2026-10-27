package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SeedanceRealPersonRejectionTest {
    @Test void acceptsExplicitImageRealPersonRejectionsAcrossProviderWording() {
        assertThat(SeedanceRealPersonRejection.matches("InputImageSensitiveContentDetected: The request failed because the input image may contain real person." )).isTrue();
        assertThat(SeedanceRealPersonRejection.matches("The image may contain realperson" )).isTrue();
        assertThat(SeedanceRealPersonRejection.matches("input images may contain real people" )).isTrue();
    }
    @Test void rejectsGenericModerationAndTransportFailures() {
        assertThat(SeedanceRealPersonRejection.matches("InputImageSensitiveContentDetected: policy violation")).isFalse();
        assertThat(SeedanceRealPersonRejection.matches("InputVideoSensitiveContentDetected: video may contain real person")).isFalse();
        assertThat(SeedanceRealPersonRejection.matches("timeout")).isFalse();
        assertThat(SeedanceRealPersonRejection.matches(null)).isFalse();
    }
    @Test void onlyDefinitiveValidationRejectionsQualifyAtSubmissionTime() {
        String message="image may contain realperson";
        assertThat(SeedanceRealPersonRejection.matches(com.antshorttv.common.ErrorCode.VALIDATION_ERROR,message)).isTrue();
        assertThat(SeedanceRealPersonRejection.matches(com.antshorttv.common.ErrorCode.AI_AUTH_FAILED,message)).isFalse();
        assertThat(SeedanceRealPersonRejection.matches(com.antshorttv.common.ErrorCode.AI_RATE_LIMIT,message)).isFalse();
        assertThat(SeedanceRealPersonRejection.matches(com.antshorttv.common.ErrorCode.AI_PROVIDER_TIMEOUT,message)).isFalse();
    }

}
