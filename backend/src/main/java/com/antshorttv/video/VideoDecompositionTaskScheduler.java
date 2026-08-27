package com.antshorttv.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai.video-decomposition.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class VideoDecompositionTaskScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoDecompositionTaskScheduler.class);

    private final VideoDecompositionEpisodeMapper episodeMapper;
    private final VideoDecompositionExecutionService executionService;
    private final AiTaskExecutionSupport executionSupport;
    private final int batchSize;

    public VideoDecompositionTaskScheduler(
        VideoDecompositionEpisodeMapper episodeMapper,
        VideoDecompositionExecutionService executionService,
        AiTaskExecutionSupport executionSupport,
        @Value("${ai.video-decomposition.scheduler.batch-size:1}") int batchSize
    ) {
        this.episodeMapper = episodeMapper;
        this.executionService = executionService;
        this.executionSupport = executionSupport;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${ai.video-decomposition.scheduler.fixed-delay-ms:10000}")
    public void pollPendingEpisodes() {
        List<VideoDecompositionEpisodeEntity> episodes = episodeMapper.selectList(
            new LambdaQueryWrapper<VideoDecompositionEpisodeEntity>()
                .in(VideoDecompositionEpisodeEntity::getStatus, List.of("PENDING_ANALYSIS", "PENDING_DRAFT"))
                .orderByAsc(VideoDecompositionEpisodeEntity::getCreatedAt)
                .last("limit " + batchSize)
        );
        for (VideoDecompositionEpisodeEntity episode : episodes) {
            try {
                AiTaskExecutionSupport.ClaimResult claim = claim(episode);
                if (claim.claimed()) {
                    executionService.executeEpisode(episode.getId());
                }
            } catch (Exception exception) {
                LOGGER.warn("Video decomposition episode execution failed before provider completion. episodeId={}", episode.getId(), exception);
                executionService.recoverClaimedEpisode(episode.getId(), exception.getMessage());
            }
        }
    }

    private AiTaskExecutionSupport.ClaimResult claim(VideoDecompositionEpisodeEntity episode) {
        if ("PENDING_DRAFT".equals(episode.getStatus())) {
            return executionSupport.claimVideoDecompositionEpisode(
                episode.getId(),
                "PENDING_DRAFT",
                "DRAFT_GENERATING",
                "DRAFT_GENERATION",
                Duration.ofMinutes(30)
            );
        }
        return executionSupport.claimVideoDecompositionEpisode(
            episode.getId(),
            "PENDING_ANALYSIS",
            "ANALYZING",
            "VIDEO_ANALYSIS",
            Duration.ofMinutes(30)
        );
    }
}
