package com.antshorttv.video;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

record VideoDecompositionBatchProgress(
    String status,
    int percentage,
    int total,
    int succeeded,
    int failed,
    int processing,
    int pending
) {
    private static final Set<String> SUCCEEDED = Set.of(
        "SUCCEEDED", "ANALYSIS_SUCCEEDED", "PENDING_REVIEW", "CONFIRMED"
    );
    private static final Set<String> PROCESSING = Set.of("ANALYZING", "DRAFT_GENERATING");
    private static final Set<String> PENDING = Set.of("PENDING_ANALYSIS", "PENDING_DRAFT");

    static VideoDecompositionBatchProgress fromEpisodes(List<VideoDecompositionEpisodeEntity> episodes) {
        return fromEpisodes(episodes, ignored -> null);
    }

    static VideoDecompositionBatchProgress fromEpisodes(
        List<VideoDecompositionEpisodeEntity> episodes,
        Function<VideoDecompositionEpisodeEntity, Integer> persistedProgress
    ) {
        List<String> statuses = episodes.stream().map(VideoDecompositionEpisodeEntity::getStatus).toList();
        int percentage = episodes.isEmpty() ? 0 : episodes.stream()
            .mapToInt(episode -> episodePercentage(episode.getStatus(), persistedProgress.apply(episode)))
            .sum() / episodes.size();
        return fromStatuses(statuses, percentage);
    }

    static VideoDecompositionBatchProgress fromStatuses(List<String> statuses) {
        int percentage = statuses.isEmpty() ? 0 : statuses.stream()
            .mapToInt(status -> episodePercentage(status, null))
            .sum() / statuses.size();
        return fromStatuses(statuses, percentage);
    }

    private static VideoDecompositionBatchProgress fromStatuses(List<String> statuses, int percentage) {
        int total = statuses.size();
        int succeeded = (int) statuses.stream().filter(SUCCEEDED::contains).count();
        int failed = (int) statuses.stream().filter("FAILED"::equals).count();
        int processing = (int) statuses.stream().filter(PROCESSING::contains).count();
        int known = succeeded + failed + processing;
        int pending = total - known;
        String status;
        if (total == 0 || pending == total) status = "PENDING";
        else if (succeeded == total) status = "SUCCEEDED";
        else if (failed == total) status = "FAILED";
        else if (succeeded + failed == total) status = "PARTIAL_FAILED";
        else if (processing > 0) status = "RUNNING";
        else status = "PENDING";
        return new VideoDecompositionBatchProgress(
            status, percentage, total, succeeded, failed, processing, pending
        );
    }

    static int episodePercentage(String status, Integer persistedProgress) {
        if (SUCCEEDED.contains(status)) return 100;
        if ("FAILED".equals(status)) {
            int value = persistedProgress == null ? 0 : persistedProgress;
            return Math.max(0, Math.min(99, value));
        }
        if (PROCESSING.contains(status)) {
            int value = persistedProgress == null ? 50 : persistedProgress;
            return Math.max(1, Math.min(99, value));
        }
        return 0;
    }
}
