package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StoryboardBatchServiceTest {

    @Test
    void mapsExecutionOutcomesWithoutTreatingWarningsAsFailures() {
        assertThat(StoryboardBatchService.itemStatus("PENDING", 0)).isEqualTo("PENDING");
        assertThat(StoryboardBatchService.itemStatus("RUNNING", 0)).isEqualTo("RUNNING");
        assertThat(StoryboardBatchService.itemStatus("SUCCEEDED", 0)).isEqualTo("SUCCESS");
        assertThat(StoryboardBatchService.itemStatus("SUCCEEDED", 2))
            .isEqualTo("SUCCESS_WITH_WARNING");
        assertThat(StoryboardBatchService.itemStatus("FAILED", 0)).isEqualTo("FAILED");
        assertThat(StoryboardBatchService.itemStatus("CANCELED", 0)).isEqualTo("FAILED");
        assertThat(StoryboardBatchService.itemStatus("TIMED_OUT", 0)).isEqualTo("FAILED");
    }

    @Test
    void reportsMixedTerminalBatchSeparatelyFromCompleteSuccess() {
        assertThat(StoryboardBatchService.batchStatus(3, 0, 0, 1, 1, 1))
            .isEqualTo("COMPLETED_WITH_FAILURES");
        assertThat(StoryboardBatchService.batchStatus(2, 0, 0, 1, 1, 0))
            .isEqualTo("SUCCEEDED_WITH_WARNING");
        assertThat(StoryboardBatchService.batchStatus(2, 0, 0, 2, 0, 0))
            .isEqualTo("SUCCEEDED");
        assertThat(StoryboardBatchService.batchStatus(2, 1, 1, 0, 0, 0))
            .isEqualTo("RUNNING");
    }
}
