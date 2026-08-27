package com.antshorttv.video;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class VideoDecompositionTaskSchedulerTest {

    @Test
    void recoversClaimWhenEpisodeExecutionThrows() {
        VideoDecompositionEpisodeEntity episode = new VideoDecompositionEpisodeEntity();
        episode.setId(42L);
        episode.setStatus("PENDING_ANALYSIS");

        VideoDecompositionEpisodeMapper episodeMapper = org.mockito.Mockito.mock(VideoDecompositionEpisodeMapper.class);
        VideoDecompositionExecutionService executionService = org.mockito.Mockito.mock(VideoDecompositionExecutionService.class);
        AiTaskExecutionSupport executionSupport = org.mockito.Mockito.mock(AiTaskExecutionSupport.class);
        when(episodeMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(episode));
        when(executionSupport.claimVideoDecompositionEpisode(
            org.mockito.ArgumentMatchers.eq(42L),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(new AiTaskExecutionSupport.ClaimResult(true, "claim-token", 1, "key"));
        doThrow(new IllegalStateException("pre-provider failure"))
            .when(executionService).executeEpisode(42L);

        new VideoDecompositionTaskScheduler(episodeMapper, executionService, executionSupport, 1)
            .pollPendingEpisodes();

        verify(executionService).recoverClaimedEpisode(42L, "pre-provider failure");
    }
}
