package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VideoDecompositionBatchProgressTest {

    @Test
    void aggregatesPendingRunningAndTerminalStatesWithoutLosingEpisodes() {
        assertProgress(List.of("PENDING_ANALYSIS", "PENDING_ANALYSIS"), "PENDING", 0, 0, 0, 2);
        assertProgress(List.of("PENDING_ANALYSIS", "ANALYZING", "SUCCEEDED", "FAILED"),
            "RUNNING", 1, 1, 1, 1);
        assertProgress(List.of("SUCCEEDED", "SUCCEEDED"), "SUCCEEDED", 2, 0, 0, 0);
        assertProgress(List.of("FAILED", "FAILED"), "FAILED", 0, 2, 0, 0);
        assertProgress(List.of("SUCCEEDED", "FAILED"), "PARTIAL_FAILED", 1, 1, 0, 0);
    }

    @Test
    void treatsRetryAndHistoricalWorkflowStatesCompatibly() {
        assertProgress(List.of("PENDING_ANALYSIS", "SUCCEEDED"), "PENDING", 1, 0, 0, 1);
        assertProgress(List.of("PENDING_DRAFT", "DRAFT_GENERATING", "PENDING_REVIEW", "CONFIRMED"),
            "RUNNING", 2, 0, 1, 1);
    }

    @Test
    void averagesPersistedEpisodeProgressAndPreservesFailedTerminalProgress() {
        VideoDecompositionEpisodeEntity running = episode(1L, "ANALYZING");
        VideoDecompositionEpisodeEntity failed = episode(2L, "FAILED");
        VideoDecompositionEpisodeEntity succeeded = episode(3L, "SUCCEEDED");

        VideoDecompositionBatchProgress progress = VideoDecompositionBatchProgress.fromEpisodes(
            List.of(running, failed, succeeded),
            episode -> Map.of(1L, 40, 2L, 10, 3L, 70).get(episode.getId())
        );

        assertThat(progress.percentage()).isEqualTo(50);
        assertThat(VideoDecompositionBatchProgress.episodePercentage("FAILED", 10)).isEqualTo(10);
    }

    private VideoDecompositionEpisodeEntity episode(Long id, String status) {
        VideoDecompositionEpisodeEntity episode = new VideoDecompositionEpisodeEntity();
        episode.setId(id);
        episode.setStatus(status);
        return episode;
    }

    private void assertProgress(
        List<String> statuses,
        String expectedStatus,
        int succeeded,
        int failed,
        int processing,
        int pending
    ) {
        VideoDecompositionBatchProgress progress = VideoDecompositionBatchProgress.fromStatuses(statuses);
        assertThat(progress.status()).isEqualTo(expectedStatus);
        assertThat(progress.succeeded()).isEqualTo(succeeded);
        assertThat(progress.failed()).isEqualTo(failed);
        assertThat(progress.processing()).isEqualTo(processing);
        assertThat(progress.pending()).isEqualTo(pending);
        assertThat(progress.succeeded() + progress.failed() + progress.processing() + progress.pending())
            .isEqualTo(progress.total());
        assertThat(progress.percentage()).isBetween(0, 100);
    }
}
