package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StoryboardValidationResultTest {
    @Test
    void classifiesOnlyScopeOwnershipAndStaleFingerprintFailuresAsFatal() {
        assertThat(StoryboardValidationResult.classify("AUTHORIZATION_FAILED"))
            .isEqualTo(StoryboardValidationResult.Severity.FATAL);
        assertThat(StoryboardValidationResult.classify("EXECUTION_OWNERSHIP_LOST"))
            .isEqualTo(StoryboardValidationResult.Severity.FATAL);
        assertThat(StoryboardValidationResult.classify("ASSET_OWNERSHIP_INVALID"))
            .isEqualTo(StoryboardValidationResult.Severity.FATAL);
        assertThat(StoryboardValidationResult.classify("STALE_FINGERPRINT"))
            .isEqualTo(StoryboardValidationResult.Severity.FATAL);
        assertThat(StoryboardValidationResult.classify("SOURCE_ANCHOR_REORDERED"))
            .isEqualTo(StoryboardValidationResult.Severity.REPAIRABLE);
        assertThat(StoryboardValidationResult.classify("ASSET_PENDING"))
            .isEqualTo(StoryboardValidationResult.Severity.REPAIRABLE);
        assertThat(StoryboardValidationResult.classify("ACTION_DENSITY"))
            .isEqualTo(StoryboardValidationResult.Severity.WARNING);
    }
}
