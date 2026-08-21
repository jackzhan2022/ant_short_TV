package com.antshorttv.video;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiTextRequest;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.ai.PromptTemplateRenderer;
import com.antshorttv.common.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoDecompositionExecutionService {
    private final VideoDecompositionBatchMapper batchMapper;
    private final VideoDecompositionEpisodeMapper episodeMapper;
    private final VideoDecompositionAnalysisMapper analysisMapper;
    private final VideoDecompositionAttemptMapper attemptMapper;
    private final ModelAccessibleVideoUrlResolver videoUrlResolver;
    private final VideoAnalysisNormalizer normalizer;
    private final AiInvocationService aiInvocationService;
    private final ProjectAiConfigService projectAiConfigService;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final JdbcTemplate jdbcTemplate;
    private final AiTaskExecutionSupport executionSupport;

    public VideoDecompositionExecutionService(
        VideoDecompositionBatchMapper batchMapper,
        VideoDecompositionEpisodeMapper episodeMapper,
        VideoDecompositionAnalysisMapper analysisMapper,
        VideoDecompositionAttemptMapper attemptMapper,
        ModelAccessibleVideoUrlResolver videoUrlResolver,
        VideoAnalysisNormalizer normalizer,
        AiInvocationService aiInvocationService,
        ProjectAiConfigService projectAiConfigService,
        PromptTemplateRenderer promptTemplateRenderer,
        JdbcTemplate jdbcTemplate,
        AiTaskExecutionSupport executionSupport
    ) {
        this.batchMapper = batchMapper;
        this.episodeMapper = episodeMapper;
        this.analysisMapper = analysisMapper;
        this.attemptMapper = attemptMapper;
        this.videoUrlResolver = videoUrlResolver;
        this.normalizer = normalizer;
        this.aiInvocationService = aiInvocationService;
        this.projectAiConfigService = projectAiConfigService;
        this.promptTemplateRenderer = promptTemplateRenderer;
        this.jdbcTemplate = jdbcTemplate;
        this.executionSupport = executionSupport;
    }

    @Transactional
    public void executeEpisode(Long episodeId) {
        VideoDecompositionEpisodeEntity episode = episodeMapper.selectById(episodeId);
        if (episode == null) {
            return;
        }
        VideoDecompositionBatchEntity batch = batchMapper.selectById(episode.getBatchId());
        if (batch == null) {
            return;
        }
        if ("PENDING_ANALYSIS".equals(episode.getStatus())) {
            AiTaskExecutionSupport.ClaimResult claim = executionSupport.claimVideoDecompositionEpisode(
                episodeId,
                "PENDING_ANALYSIS",
                "ANALYZING",
                "VIDEO_ANALYSIS",
                Duration.ofMinutes(30)
            );
            if (!claim.claimed()) {
                return;
            }
            applyClaim(episode, claim, "ANALYZING", "VIDEO_ANALYSIS");
        } else if ("PENDING_DRAFT".equals(episode.getStatus())) {
            AiTaskExecutionSupport.ClaimResult claim = executionSupport.claimVideoDecompositionEpisode(
                episodeId,
                "PENDING_DRAFT",
                "DRAFT_GENERATING",
                "DRAFT_GENERATION",
                Duration.ofMinutes(30)
            );
            if (!claim.claimed()) {
                return;
            }
            applyClaim(episode, claim, "DRAFT_GENERATING", "DRAFT_GENERATION");
        }
        if (episode.getExecutionToken() == null) {
            return;
        }
        if ("DRAFT_GENERATING".equals(episode.getStatus())) {
            executeDraftGeneration(episode, latestSuccessfulAnalysis(episodeId));
            recalculateBatch(episode.getTenantId(), episode.getBatchId());
            return;
        }
        if (!"ANALYZING".equals(episode.getStatus())) {
            return;
        }
        VideoDecompositionAttemptEntity attempt = currentAttempt(episodeId, "VIDEO_ANALYSIS");
        LocalDateTime now = LocalDateTime.now();
        markRunning(episode, attempt, now);

        AiInvocationResult<VideoUnderstandingResponse> callResult = null;
        String rawResponse = null;
        try {
            String videoUrl = videoUrlResolver.resolve(episode.getStoragePath());
            callResult = aiInvocationService.invokeVideoUnderstanding(AiInvocationRequest.videoUnderstanding()
                .tenantId(episode.getTenantId())
                .userId(episode.getCreatedBy())
                .projectId(episode.getProjectId())
                .taskId(episode.getId())
                .modelId(batch.getModelId())
                .scene(AiBusinessScene.VIDEO_UNDERSTANDING)
                .traceId("video-decomposition-%d".formatted(episode.getId()))
                .videoRequest(new VideoUnderstandingRequest(videoUrl, structuredPrompt(episode)))
                .build());
            rawResponse = callResult.content();
            VideoAnalysis analysis = normalizer.normalize(rawResponse);
            insertAnalysis(episode, "SUCCEEDED", rawResponse, analysis.normalizedJson(), callResult);
            episode.setStatus("ANALYSIS_SUCCEEDED");
            episode.setAnalysisVersion(episode.getAnalysisVersion() + 1);
            episode.setErrorCode(null);
            episode.setErrorMessage(null);
            episode.setUpdatedAt(LocalDateTime.now());
            episodeMapper.updateById(episode);
            markAttempt(attempt, "SUCCEEDED", callResult.response().providerRequestId(), callResult.aiCallLogId(), null, null);
            prepareDraftExecution(episode);
            executeDraftGeneration(episode, analysis.normalizedJson());
        } catch (VideoAnalysisParseException exception) {
            if (callResult != null) {
                aiInvocationService.markBusinessFailure(callResult.aiCallLogId(), ErrorCode.AI_RESPONSE_INVALID, exception.getMessage());
            }
            insertAnalysis(episode, "FAILED", rawResponse, null, callResult);
            failEpisode(episode, attempt, "AI_RESPONSE_INVALID", exception.getMessage(), callResult);
        } catch (AiGatewayException exception) {
            failEpisode(episode, attempt, exception.getErrorCode().name(), exception.getMessage(), callResult, exception.getAiCallLogId());
        } catch (Exception exception) {
            failEpisode(episode, attempt, "VALIDATION_ERROR", exception.getMessage(), callResult);
        } finally {
            recalculateBatch(episode.getTenantId(), episode.getBatchId());
        }
    }

    private void executeDraftGeneration(VideoDecompositionEpisodeEntity episode, String normalizedJson) {
        VideoDecompositionAttemptEntity attempt = currentAttempt(episode.getId(), "DRAFT_GENERATION");
        LocalDateTime now = LocalDateTime.now();
        episode.setStatus("DRAFT_GENERATING");
        episode.setDraftStatus("GENERATING");
        episode.setExecutionPhase("DRAFT_GENERATION");
        episode.setUpdatedAt(now);
        episodeMapper.updateById(episode);
        attempt.setIdempotencyKey(executionSupport.idempotencyKey(
            "VIDEO_DECOMPOSITION",
            episode.getId(),
            "DRAFT_GENERATION",
            safeVersion(episode.getExecutionVersion())
        ));
        attempt.setStatus("RUNNING");
        attempt.setStartedAt(now);
        attempt.setRetryable(false);
        attemptMapper.updateById(attempt);

        try {
            if (normalizedJson == null || normalizedJson.isBlank()) {
                throw new AiGatewayException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "缺少可用于生成剧本的结构化解析结果。");
            }
            Long textModelId = projectAiConfigService.resolveModelId(episode.getTenantId(), episode.getProjectId(), "TEXT");
            AiInvocationResult<AiTextResponse> invocation = aiInvocationService.invokeText(AiInvocationRequest.text()
                .tenantId(episode.getTenantId())
                .userId(episode.getCreatedBy())
                .projectId(episode.getProjectId())
                .taskId(episode.getId())
                .modelId(textModelId)
                .scene(AiBusinessScene.VIDEO_SCRIPT_DRAFT)
                .traceId("video-script-draft-%d-v%d".formatted(episode.getId(), episode.getAnalysisVersion()))
                .textRequest(new AiTextRequest(
                    "你是短剧编剧，请根据结构化视频拆解结果输出可直接审核的中文分集剧本。",
                    draftPrompt(episode, normalizedJson),
                    0.7,
                    4096,
                    null
                ))
                .build());
            episode.setDraftContent(invocation.content());
            episode.setDraftStatus("PENDING_REVIEW");
            episode.setDraftVersion((episode.getDraftVersion() == null ? 0 : episode.getDraftVersion()) + 1);
            episode.setStatus("PENDING_REVIEW");
            episode.setErrorCode(null);
            episode.setErrorMessage(null);
            clearExecution(episode, false);
            episode.setUpdatedAt(LocalDateTime.now());
            episodeMapper.updateById(episode);
            clearExecutionColumns(episode.getId(), false);
            markAttempt(attempt, "SUCCEEDED", invocation.providerRequestId(), invocation.aiCallLogId(), null, null);
        } catch (AiGatewayException exception) {
            failDraft(episode, attempt, exception.getErrorCode().name(), exception.getMessage(), exception.getAiCallLogId());
        } catch (Exception exception) {
            failDraft(episode, attempt, "AI_PROVIDER_ERROR", exception.getMessage(), null);
        }
    }

    private void markRunning(VideoDecompositionEpisodeEntity episode, VideoDecompositionAttemptEntity attempt, LocalDateTime now) {
        episode.setStatus("ANALYZING");
        episode.setUpdatedAt(now);
        episodeMapper.updateById(episode);
        attempt.setIdempotencyKey(executionSupport.idempotencyKey(
            "VIDEO_DECOMPOSITION",
            episode.getId(),
            "VIDEO_ANALYSIS",
            safeVersion(episode.getExecutionVersion())
        ));
        attempt.setStatus("RUNNING");
        attempt.setStartedAt(now);
        attempt.setRetryable(false);
        attemptMapper.updateById(attempt);
    }

    private void failEpisode(
        VideoDecompositionEpisodeEntity episode,
        VideoDecompositionAttemptEntity attempt,
        String errorCode,
        String errorMessage,
        AiInvocationResult<VideoUnderstandingResponse> callResult
    ) {
        failEpisode(episode, attempt, errorCode, errorMessage, callResult, null);
    }

    private void failEpisode(
        VideoDecompositionEpisodeEntity episode,
        VideoDecompositionAttemptEntity attempt,
        String errorCode,
        String errorMessage,
        AiInvocationResult<VideoUnderstandingResponse> callResult,
        Long fallbackCallLogId
    ) {
        episode.setStatus("FAILED");
        episode.setErrorCode(errorCode);
        episode.setErrorMessage(errorMessage);
        clearExecution(episode, true);
        episode.setUpdatedAt(LocalDateTime.now());
        jdbcTemplate.update("""
            update video_decomposition_episode
               set status = 'FAILED',
                   error_code = ?,
                   error_message = ?,
                   execution_token = null,
                   execution_phase = null,
                   heartbeat_at = null,
                   execution_timeout_at = null,
                   retryable = true,
                   updated_at = now()
             where id = ?
            """, errorCode, errorMessage, episode.getId());
        markAttempt(
            attempt,
            "FAILED",
            callResult == null ? null : callResult.providerRequestId(),
            callResult == null ? fallbackCallLogId : callResult.aiCallLogId(),
            errorCode,
            errorMessage
        );
    }

    private void markAttempt(
        VideoDecompositionAttemptEntity attempt,
        String status,
        String providerRequestId,
        Long callLogId,
        String errorCode,
        String errorMessage
    ) {
        attempt.setStatus(status);
        attempt.setProviderRequestId(providerRequestId);
        attempt.setAiCallLogId(callLogId);
        attempt.setErrorCode(errorCode);
        attempt.setErrorMessage(errorMessage);
        attempt.setRetryable("FAILED".equals(status));
        attempt.setFinishedAt(LocalDateTime.now());
        attemptMapper.updateById(attempt);
    }

    private void insertAnalysis(
        VideoDecompositionEpisodeEntity episode,
        String status,
        String rawResponse,
        String normalizedJson,
        AiInvocationResult<VideoUnderstandingResponse> callResult
    ) {
        VideoDecompositionAnalysisEntity entity = new VideoDecompositionAnalysisEntity();
        entity.setEpisodeId(episode.getId());
        entity.setSchemaVersion("v1");
        entity.setStatus(status);
        entity.setRawResponse(rawResponse);
        entity.setNormalizedJson(normalizedJson);
        entity.setProviderRequestId(callResult == null ? null : callResult.providerRequestId());
        entity.setAiCallLogId(callResult == null ? null : callResult.aiCallLogId());
        entity.setCreatedAt(LocalDateTime.now());
        analysisMapper.insert(entity);
    }

    private void failDraft(
        VideoDecompositionEpisodeEntity episode,
        VideoDecompositionAttemptEntity attempt,
        String errorCode,
        String errorMessage,
        Long callLogId
    ) {
        episode.setStatus("FAILED");
        episode.setDraftStatus("FAILED");
        episode.setErrorCode(errorCode);
        episode.setErrorMessage(errorMessage);
        clearExecution(episode, true);
        episode.setUpdatedAt(LocalDateTime.now());
        jdbcTemplate.update("""
            update video_decomposition_episode
               set status = 'FAILED',
                   draft_status = 'FAILED',
                   error_code = ?,
                   error_message = ?,
                   execution_token = null,
                   execution_phase = null,
                   heartbeat_at = null,
                   execution_timeout_at = null,
                   retryable = true,
                   updated_at = now()
             where id = ?
            """, errorCode, errorMessage, episode.getId());
        markAttempt(attempt, "FAILED", null, callLogId, errorCode, errorMessage);
    }

    private VideoDecompositionAttemptEntity currentAttempt(Long episodeId, String phase) {
        VideoDecompositionAttemptEntity attempt = attemptMapper.selectOne(new LambdaQueryWrapper<VideoDecompositionAttemptEntity>()
            .eq(VideoDecompositionAttemptEntity::getEpisodeId, episodeId)
            .eq(VideoDecompositionAttemptEntity::getPhase, phase)
            .orderByDesc(VideoDecompositionAttemptEntity::getAttemptNo)
            .last("limit 1"));
        if (attempt != null) {
            return attempt;
        }
        attempt = new VideoDecompositionAttemptEntity();
        attempt.setEpisodeId(episodeId);
        attempt.setAttemptNo(1);
        attempt.setPhase(phase);
        attempt.setStatus("PENDING");
        attempt.setStartedAt(LocalDateTime.now());
        attemptMapper.insert(attempt);
        return attempt;
    }

    private void recalculateBatch(Long tenantId, Long batchId) {
        List<VideoDecompositionEpisodeEntity> episodes = episodeMapper.selectByBatch(tenantId, batchId);
        int failed = (int) episodes.stream().filter(item -> "FAILED".equals(item.getStatus())).count();
        int completed = (int) episodes.stream().filter(item -> List.of("ANALYSIS_SUCCEEDED", "PENDING_REVIEW", "CONFIRMED").contains(item.getStatus())).count();
        VideoDecompositionBatchEntity batch = batchMapper.selectById(batchId);
        batch.setFailedEpisodes(failed);
        batch.setCompletedEpisodes(completed);
        batch.setStatus(failed > 0 ? "PARTIAL_FAILED" : completed == batch.getTotalEpisodes() ? "ANALYSIS_SUCCEEDED" : "ANALYZING");
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    private String structuredPrompt(VideoDecompositionEpisodeEntity episode) {
        return promptTemplateRenderer.render(
            AiBusinessScene.VIDEO_UNDERSTANDING.promptTemplateId(),
            Map.of("episodeNo", episode.getEpisodeNo())
        );
    }

    private String latestSuccessfulAnalysis(Long episodeId) {
        VideoDecompositionAnalysisEntity analysis = analysisMapper.selectOne(new LambdaQueryWrapper<VideoDecompositionAnalysisEntity>()
            .eq(VideoDecompositionAnalysisEntity::getEpisodeId, episodeId)
            .eq(VideoDecompositionAnalysisEntity::getStatus, "SUCCEEDED")
            .orderByDesc(VideoDecompositionAnalysisEntity::getCreatedAt)
            .last("limit 1"));
        return analysis == null ? null : analysis.getNormalizedJson();
    }

    private void prepareDraftExecution(VideoDecompositionEpisodeEntity episode) {
        episode.setExecutionPhase("DRAFT_GENERATION");
        episode.setHeartbeatAt(LocalDateTime.now());
        episode.setExecutionTimeoutAt(LocalDateTime.now().plusMinutes(30));
        episodeMapper.updateById(episode);
    }

    private void applyClaim(
        VideoDecompositionEpisodeEntity episode,
        AiTaskExecutionSupport.ClaimResult claim,
        String status,
        String phase
    ) {
        episode.setStatus(status);
        episode.setExecutionToken(claim.executionToken());
        episode.setExecutionPhase(phase);
        episode.setExecutionVersion(claim.executionVersion());
        episode.setRetryable(false);
    }

    private void clearExecution(VideoDecompositionEpisodeEntity episode, boolean retryable) {
        episode.setExecutionToken(null);
        episode.setExecutionPhase(null);
        episode.setHeartbeatAt(null);
        episode.setExecutionTimeoutAt(null);
        episode.setRetryable(retryable);
    }

    private void clearExecutionColumns(Long episodeId, boolean retryable) {
        jdbcTemplate.update("""
            update video_decomposition_episode
               set execution_token = null,
                   execution_phase = null,
                   heartbeat_at = null,
                   execution_timeout_at = null,
                   retryable = ?
             where id = ?
            """, retryable, episodeId);
    }

    private int safeVersion(Integer version) {
        return version == null ? 0 : version;
    }

    private String draftPrompt(VideoDecompositionEpisodeEntity episode, String normalizedJson) {
        return promptTemplateRenderer.render(
            AiBusinessScene.VIDEO_SCRIPT_DRAFT.promptTemplateId(),
            Map.of("episodeNo", episode.getEpisodeNo(), "normalizedJson", normalizedJson)
        );
    }
}
