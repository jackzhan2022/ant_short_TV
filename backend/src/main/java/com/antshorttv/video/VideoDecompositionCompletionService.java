package com.antshorttv.video;

import com.antshorttv.ai.AiInvocationResult;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoDecompositionCompletionService {
    private final VideoDecompositionAnalysisMapper analysisMapper;
    private final VideoDecompositionEpisodeMapper episodeMapper;
    private final VideoDecompositionScriptResultRepository resultRepository;
    private final JdbcTemplate jdbcTemplate;

    public VideoDecompositionCompletionService(
        VideoDecompositionAnalysisMapper analysisMapper,
        VideoDecompositionEpisodeMapper episodeMapper,
        VideoDecompositionScriptResultRepository resultRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.analysisMapper = analysisMapper;
        this.episodeMapper = episodeMapper;
        this.resultRepository = resultRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public VideoDecompositionScriptResult complete(
        VideoDecompositionEpisodeEntity episode,
        VideoAnalysis analysis,
        String rawResponse,
        AiInvocationResult<VideoUnderstandingResponse> callResult
    ) {
        VideoDecompositionAnalysisEntity evidence = new VideoDecompositionAnalysisEntity();
        evidence.setEpisodeId(episode.getId());
        evidence.setExecutionId(episode.getExecutionId());
        evidence.setSchemaVersion("v1");
        evidence.setStatus("SUCCEEDED");
        evidence.setRawResponse(rawResponse);
        evidence.setNormalizedJson(analysis.normalizedJson());
        evidence.setProviderRequestId(callResult.providerRequestId());
        evidence.setAiCallLogId(callResult.aiCallLogId());
        evidence.setCreatedAt(LocalDateTime.now());
        analysisMapper.insert(evidence);

        VideoDecompositionScriptResult result = resultRepository.insert(
            new VideoDecompositionScriptResultCreate(
                episode.getTenantId(), episode.getBatchId(), episode.getId(), evidence.getId(),
                callResult.aiCallLogId(), analysis.script(), MarkdownScreenplayValidator.FORMAT_VERSION
            )
        );

        episode.setStatus("SUCCEEDED");
        episode.setAnalysisVersion(episode.getAnalysisVersion() + 1);
        episode.setErrorCode(null);
        episode.setErrorMessage(null);
        episode.setExecutionToken(null);
        episode.setExecutionPhase(null);
        episode.setHeartbeatAt(null);
        episode.setExecutionTimeoutAt(null);
        episode.setRetryable(false);
        episode.setUpdatedAt(LocalDateTime.now());
        episodeMapper.updateById(episode);
        jdbcTemplate.update("""
            update video_decomposition_episode
               set execution_token = null,
                   execution_phase = null,
                   heartbeat_at = null,
                   execution_timeout_at = null,
                   retryable = false
             where id = ?
            """, episode.getId());
        return result;
    }
}
