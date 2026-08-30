package com.antshorttv.video;

import com.antshorttv.accounting.AiExecutionCostSummary;
import com.antshorttv.accounting.AiUsageAccountingService;
import com.antshorttv.accounting.AiUsageCommand;
import com.antshorttv.accounting.AiUsageContext;
import com.antshorttv.accounting.AiUsageMetric;
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
import com.antshorttv.execution.AiExecutionAttemptEntity;
import com.antshorttv.execution.AiExecutionAttemptMapper;
import com.antshorttv.execution.AiExecutionCreateCommand;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.points.AiSettlementOutcome;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private final VideoDecompositionCompletionService completionService;
    private final AiInvocationService aiInvocationService;
    private final ProjectAiConfigService projectAiConfigService;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final JdbcTemplate jdbcTemplate;
    private final AiTaskExecutionSupport executionSupport;
    private final AiExecutionService executionService;
    private final AiExecutionTaskMapper executionTaskMapper;
    private final AiExecutionAttemptMapper executionAttemptMapper;
    private final AiUsageAccountingService usageAccountingService;
    private final AiPointReservationMapper reservationMapper;
    private final AiPointSettlementService pointSettlementService;
    private final ObjectMapper objectMapper;

    public VideoDecompositionExecutionService(
        VideoDecompositionBatchMapper batchMapper,
        VideoDecompositionEpisodeMapper episodeMapper,
        VideoDecompositionAnalysisMapper analysisMapper,
        VideoDecompositionAttemptMapper attemptMapper,
        ModelAccessibleVideoUrlResolver videoUrlResolver,
        VideoAnalysisNormalizer normalizer,
        VideoDecompositionCompletionService completionService,
        AiInvocationService aiInvocationService,
        ProjectAiConfigService projectAiConfigService,
        PromptTemplateRenderer promptTemplateRenderer,
        JdbcTemplate jdbcTemplate,
        AiTaskExecutionSupport executionSupport,
        AiExecutionService executionService,
        AiExecutionTaskMapper executionTaskMapper,
        AiExecutionAttemptMapper executionAttemptMapper,
        AiUsageAccountingService usageAccountingService,
        AiPointReservationMapper reservationMapper,
        AiPointSettlementService pointSettlementService,
        ObjectMapper objectMapper
    ) {
        this.batchMapper = batchMapper;
        this.episodeMapper = episodeMapper;
        this.analysisMapper = analysisMapper;
        this.attemptMapper = attemptMapper;
        this.videoUrlResolver = videoUrlResolver;
        this.normalizer = normalizer;
        this.completionService = completionService;
        this.aiInvocationService = aiInvocationService;
        this.projectAiConfigService = projectAiConfigService;
        this.promptTemplateRenderer = promptTemplateRenderer;
        this.jdbcTemplate = jdbcTemplate;
        this.executionSupport = executionSupport;
        this.executionService = executionService;
        this.executionTaskMapper = executionTaskMapper;
        this.executionAttemptMapper = executionAttemptMapper;
        this.usageAccountingService = usageAccountingService;
        this.reservationMapper = reservationMapper;
        this.pointSettlementService = pointSettlementService;
        this.objectMapper = objectMapper;
    }

    public void executeEpisode(Long episodeId) {
        VideoDecompositionEpisodeEntity episode = episodeMapper.selectById(episodeId);
        if (episode == null) {
            return;
        }
        VideoDecompositionBatchEntity batch = batchMapper.selectById(episode.getBatchId());
        if (batch == null) {
            return;
        }
        if (!List.of("PENDING_ANALYSIS", "ANALYZING", "PENDING_DRAFT", "DRAFT_GENERATING")
            .contains(episode.getStatus())) {
            return;
        }
        ensureExecutionHeader(episode, batch);
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
        AiExecutionAttemptEntity sharedAttempt = startSharedAttempt(episode, attempt, "VIDEO_ANALYSIS");
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
                .traceId(executionService.requireTask(episode.getExecutionId()).traceId)
                .executionId(episode.getExecutionId())
                .attemptId(sharedAttempt.id)
                .executionVersion(sharedAttempt.executionVersion)
                .phase(sharedAttempt.phase)
                .idempotencyKey(sharedAttempt.idempotencyKey)
                .videoRequest(new VideoUnderstandingRequest(videoUrl, structuredPrompt(episode)))
                .build());
            rawResponse = callResult.content();
            VideoAnalysis analysis = normalizer.normalize(rawResponse, episode.getEpisodeNo());
            markAttempt(attempt, "SUCCEEDED", callResult.response().providerRequestId(), callResult.aiCallLogId(), null, null);
            finishSharedAttempt(sharedAttempt, callResult, "SUCCEEDED", false, null, null);
            completeStage(episode, sharedAttempt, callResult, AiSettlementOutcome.SUCCESS);
            updateExecution(episode, "SUCCEEDED", "VIDEO_ANALYSIS", 100,
                "VIDEO_DECOMPOSITION_EPISODE", episode.getId());
            completionService.complete(episode, analysis, rawResponse, callResult);
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
        AiExecutionAttemptEntity sharedAttempt = startSharedAttempt(episode, attempt, "DRAFT_GENERATION");
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
                .traceId(executionService.requireTask(episode.getExecutionId()).traceId)
                .executionId(episode.getExecutionId())
                .attemptId(sharedAttempt.id)
                .executionVersion(sharedAttempt.executionVersion)
                .phase(sharedAttempt.phase)
                .idempotencyKey(sharedAttempt.idempotencyKey)
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
            finishSharedAttempt(sharedAttempt, invocation, "SUCCEEDED", false, null, null);
            completeStage(episode, sharedAttempt, invocation, AiSettlementOutcome.SUCCESS);
            updateExecution(episode, "SUCCEEDED", "DRAFT_GENERATION", 100, "VIDEO_DECOMPOSITION_EPISODE", episode.getId());
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
        int terminalProgress = terminalProgress(episode);
        AiSettlementOutcome settlementOutcome = callResult == null && fallbackCallLogId != null
            && isUncertainTransportFailure(errorCode, errorMessage)
                ? AiSettlementOutcome.TRANSPORT_UNKNOWN
                : callResult == null ? AiSettlementOutcome.PROVIDER_REJECTION : AiSettlementOutcome.BUSINESS_FAILURE;
        boolean retryable = settlementOutcome != AiSettlementOutcome.TRANSPORT_UNKNOWN;
        episode.setStatus("FAILED");
        episode.setErrorCode(errorCode);
        episode.setErrorMessage(errorMessage);
        clearExecution(episode, retryable);
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
                   retryable = ?,
                   updated_at = now()
             where id = ?
            """, errorCode, errorMessage, retryable, episode.getId());
        markAttempt(
            attempt,
            "FAILED",
            callResult == null ? null : callResult.providerRequestId(),
            callResult == null ? fallbackCallLogId : callResult.aiCallLogId(),
            errorCode,
            errorMessage
        );
        finishLatestSharedFailure(
            episode,
            "VIDEO_ANALYSIS",
            callResult == null ? fallbackCallLogId : callResult.aiCallLogId(),
            errorCode,
            errorMessage
        );
        failStageSettlement(
            episode,
            attempt == null ? null : attempt.getExecutionId(),
            callResult == null ? fallbackCallLogId : callResult.aiCallLogId(),
            callResult == null ? null : callResult.resolvedModelId(),
            settlementOutcome
        );
        updateExecution(episode, "FAILED", "VIDEO_ANALYSIS", terminalProgress, null, null);
        if (!retryable) {
            executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
                .set("retryable", false)
                .eq("id", episode.getExecutionId()));
        }
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
        entity.setExecutionId(episode.getExecutionId());
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
        finishLatestSharedFailure(episode, "DRAFT_GENERATION", callLogId, errorCode, errorMessage);
        failStageSettlement(
            episode,
            episode.getExecutionId(),
            callLogId,
            null,
            callLogId == null ? AiSettlementOutcome.PROVIDER_REJECTION : AiSettlementOutcome.PROVIDER_BILLED_FAILURE
        );
        updateExecution(episode, "FAILED", "DRAFT_GENERATION", terminalProgress(episode), null, null);
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
        VideoDecompositionEpisodeEntity episode = episodeMapper.selectById(episodeId);
        attempt.setExecutionId(episode == null ? null : episode.getExecutionId());
        attempt.setAttemptNo(1);
        attempt.setPhase(phase);
        attempt.setStatus("PENDING");
        attempt.setStartedAt(LocalDateTime.now());
        attemptMapper.insert(attempt);
        return attempt;
    }

    private void recalculateBatch(Long tenantId, Long batchId) {
        List<VideoDecompositionEpisodeEntity> episodes = episodeMapper.selectByBatch(tenantId, batchId);
        VideoDecompositionBatchProgress progress = VideoDecompositionBatchProgress.fromEpisodes(
            episodes, this::persistedExecutionProgress);
        VideoDecompositionBatchEntity batch = batchMapper.selectById(batchId);
        batch.setFailedEpisodes(progress.failed());
        batch.setCompletedEpisodes(progress.succeeded());
        batch.setStatus(progress.status());
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    private String structuredPrompt(VideoDecompositionEpisodeEntity episode) {
        AiExecutionTaskEntity execution = executionService.requireTask(episode.getExecutionId());
        if (execution.redactedInputJson != null && !execution.redactedInputJson.isBlank()) {
            try {
                String snapshot = objectMapper.readTree(execution.redactedInputJson)
                    .path("screenplayPrompt").asText(null);
                if (snapshot != null && !snapshot.isBlank()) {
                    return snapshot;
                }
            } catch (Exception ignored) {
                // Historical execution rows did not contain a prompt snapshot.
            }
        }
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
        Long textModelId = projectAiConfigService.resolveModelId(
            episode.getTenantId(), episode.getProjectId(), "TEXT");
        AiExecutionTaskEntity execution = createStageExecution(
            episode, textModelId, "TEXT_GENERATION", "DRAFT_GENERATION",
            "video-decomposition:%d:draft:%d".formatted(
                episode.getId(), (episode.getDraftVersion() == null ? 0 : episode.getDraftVersion()) + 1));
        episode.setExecutionId(execution.id);
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

    private void ensureExecutionHeader(
        VideoDecompositionEpisodeEntity episode,
        VideoDecompositionBatchEntity batch
    ) {
        if (episode.getExecutionId() == null) {
            AiExecutionTaskEntity execution = createStageExecution(
                episode, batch.getModelId(), "VIDEO_UNDERSTANDING", "VIDEO_ANALYSIS",
                "video-decomposition:%d".formatted(episode.getId()));
            episode.setExecutionId(execution.id);
            episodeMapper.updateById(episode);
        }
        updateExecution(episode, "RUNNING", episode.getExecutionPhase() == null ? "VIDEO_ANALYSIS" : episode.getExecutionPhase(), 5, null, null);
    }

    private boolean isUncertainTransportFailure(String errorCode, String errorMessage) {
        return ErrorCode.AI_PROVIDER_ERROR.name().equals(errorCode)
            && errorMessage != null
            && errorMessage.startsWith("Qwen 视频理解调用失败：");
    }

    private AiExecutionTaskEntity createStageExecution(
        VideoDecompositionEpisodeEntity episode,
        Long modelId,
        String capability,
        String phase,
        String idempotencyKey
    ) {
        return executionService.createWithReservation(new AiExecutionCreateCommand(
            episode.getTenantId(),
            episode.getCreatedBy(),
            episode.getProjectId(),
            "video_decomposition_" + phase.toLowerCase(),
            capability,
            "VIDEO_DECOMPOSITION_EPISODE",
            episode.getId(),
            modelId,
            phase,
            idempotencyKey,
            UUID.randomUUID().toString(),
            true,
            "{\"episodeId\":%d,\"phase\":\"%s\"}".formatted(episode.getId(), phase)
        ), Map.of(AiUsageMetric.CALL, BigDecimal.ONE), Map.of());
    }

    private void completeStage(
        VideoDecompositionEpisodeEntity episode,
        AiExecutionAttemptEntity attempt,
        AiInvocationResult<?> invocation,
        AiSettlementOutcome outcome
    ) {
        recordCallUsageAndCost(
            episode.getTenantId(), attempt.executionId, attempt.id,
            invocation.aiCallLogId(), invocation.resolvedModelId());
        settleStage(attempt.executionId, attempt.id, invocation.aiCallLogId(), outcome, BigDecimal.ONE);
    }

    private void failStageSettlement(
        VideoDecompositionEpisodeEntity episode,
        Long executionId,
        Long callLogId,
        Long modelId,
        AiSettlementOutcome outcome
    ) {
        if (executionId == null) return;
        AiExecutionTaskEntity execution = executionService.requireTask(executionId);
        if (callLogId != null) {
            recordCallUsageAndCost(
                episode.getTenantId(), executionId, null, callLogId,
                modelId == null ? execution.requestedModelId : modelId);
        }
        settleStage(
            executionId, null, callLogId, outcome,
            callLogId == null ? BigDecimal.ZERO : BigDecimal.ONE);
    }

    private void recordCallUsageAndCost(
        Long tenantId,
        Long executionId,
        Long attemptId,
        Long callLogId,
        Long modelId
    ) {
        usageAccountingService.record(AiUsageCommand.requestDerived(
            new AiUsageContext(tenantId, executionId, attemptId, callLogId, modelId),
            AiUsageMetric.CALL, "1", Map.of(), LocalDateTime.now()));
        AiExecutionCostSummary cost = usageAccountingService.priceExecution(
            executionId, Set.of(AiUsageMetric.CALL));
        try {
            executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
                .set("usage_cost_status", cost.status().name())
                .set("provider_cost_summary_json", objectMapper.writeValueAsString(cost.totalsByCurrency()))
                .eq("id", executionId));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist video decomposition cost summary.", exception);
        }
    }

    @Transactional
    public void recoverClaimedEpisode(Long episodeId, String failureMessage) {
        VideoDecompositionEpisodeEntity episode = episodeMapper.selectById(episodeId);
        if (episode == null || episode.getExecutionToken() == null) {
            return;
        }
        String phase = episode.getExecutionPhase() == null ? "VIDEO_ANALYSIS" : episode.getExecutionPhase();
        String message = failureMessage == null || failureMessage.isBlank()
            ? "Video decomposition execution failed before provider invocation."
            : failureMessage;
        int recovered = jdbcTemplate.update("""
            update video_decomposition_episode
               set status = 'FAILED',
                   error_code = 'AI_EXECUTION_FAILED',
                   error_message = ?,
                   execution_token = null,
                   execution_phase = null,
                   heartbeat_at = null,
                   execution_timeout_at = null,
                   retryable = true,
                   updated_at = now()
             where id = ?
               and execution_token is not null
               and status in ('ANALYZING', 'DRAFT_GENERATING')
            """, message, episodeId);
        if (recovered != 1) {
            return;
        }

        VideoDecompositionAttemptEntity attempt = currentAttempt(episodeId, phase);
        if (episode.getExecutionId() != null) {
            startSharedAttempt(episode, attempt, phase);
        }
        markAttempt(attempt, "FAILED", null, null, "AI_EXECUTION_FAILED", message);
        if (episode.getExecutionId() != null) {
            finishLatestSharedFailure(episode, phase, null, "AI_EXECUTION_FAILED", message);
            failStageSettlement(episode, episode.getExecutionId(), null, null, AiSettlementOutcome.PRE_CALL_CANCELED);
            updateExecution(episode, "FAILED", phase, terminalProgress(episode), null, null);
        }
        recalculateBatch(episode.getTenantId(), episode.getBatchId());
    }

    private void settleStage(
        Long executionId,
        Long attemptId,
        Long callLogId,
        AiSettlementOutcome outcome,
        BigDecimal callCount
    ) {
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(executionId);
        if (reservation == null) {
            throw new IllegalStateException("Video decomposition execution has no point reservation.");
        }
        AiPointReservationEntity settled = pointSettlementService.finalizeOutcome(
            reservation.id,
            outcome,
            Map.of(AiUsageMetric.CALL, callCount),
            attemptId,
            callLogId,
            "execution:%d:v%d:%s".formatted(executionId, reservation.executionVersion, outcome.name().toLowerCase())
        );
        executionService.updateSettlementSummary(settled);
    }

    private AiExecutionAttemptEntity startSharedAttempt(
        VideoDecompositionEpisodeEntity episode,
        VideoDecompositionAttemptEntity domainAttempt,
        String phase
    ) {
        AiExecutionTaskEntity execution = executionService.requireTask(episode.getExecutionId());
        Long count = executionAttemptMapper.selectCount(new QueryWrapper<AiExecutionAttemptEntity>()
            .eq("execution_id", execution.id)
            .eq("execution_version", execution.executionVersion)
            .eq("phase", phase));
        int attemptNo = count.intValue() + 1;
        AiExecutionAttemptEntity attempt = new AiExecutionAttemptEntity();
        attempt.executionId = execution.id;
        attempt.executionVersion = execution.executionVersion;
        attempt.phase = phase;
        attempt.attemptNo = attemptNo;
        attempt.status = "STARTED";
        attempt.idempotencyKey = "execution:%d:v%d:%s:%d".formatted(
            execution.id, execution.executionVersion, phase, attemptNo
        );
        attempt.providerContacted = false;
        attempt.retryable = false;
        attempt.retryCount = Math.max(0, attemptNo - 1);
        attempt.startedAt = LocalDateTime.now();
        executionAttemptMapper.insert(attempt);
        domainAttempt.setExecutionId(execution.id);
        domainAttempt.setIdempotencyKey(attempt.idempotencyKey);
        attemptMapper.updateById(domainAttempt);
        return attempt;
    }

    private void finishSharedAttempt(
        AiExecutionAttemptEntity attempt,
        AiInvocationResult<?> invocation,
        String status,
        boolean retryable,
        String errorCode,
        String errorMessage
    ) {
        executionAttemptMapper.update(null, new UpdateWrapper<AiExecutionAttemptEntity>()
            .set("status", status)
            .set("provider_contacted", true)
            .set("provider_contacted_at", LocalDateTime.now())
            .set("provider_id", invocation.providerId())
            .set("model_id", invocation.resolvedModelId())
            .set("provider_request_id", invocation.providerRequestId())
            .set("ai_call_log_id", invocation.aiCallLogId())
            .set("transport_outcome", invocation.transportOutcome())
            .set("business_outcome", invocation.businessOutcome())
            .set("retryable", retryable)
            .set("error_code", errorCode)
            .set("error_message", errorMessage)
            .set("finished_at", LocalDateTime.now())
            .eq("id", attempt.id));
    }

    private void finishLatestSharedFailure(
        VideoDecompositionEpisodeEntity episode,
        String phase,
        Long callLogId,
        String errorCode,
        String errorMessage
    ) {
        AiExecutionAttemptEntity attempt = executionAttemptMapper.selectOne(
            new QueryWrapper<AiExecutionAttemptEntity>()
                .eq("execution_id", episode.getExecutionId())
                .eq("phase", phase)
                .orderByDesc("id")
                .last("limit 1")
        );
        if (attempt == null) return;
        executionAttemptMapper.update(null, new UpdateWrapper<AiExecutionAttemptEntity>()
            .set("status", "FAILED")
            .set("provider_contacted", callLogId != null)
            .set("ai_call_log_id", callLogId)
            .set("transport_outcome", "FAILED")
            .set("business_outcome", "FAILED")
            .set("retryable", true)
            .set("error_code", errorCode)
            .set("error_message", errorMessage)
            .set("finished_at", LocalDateTime.now())
            .eq("id", attempt.id));
    }

    private void updateExecution(
        VideoDecompositionEpisodeEntity episode,
        String status,
        String phase,
        int progress,
        String resultType,
        Long resultId
    ) {
        UpdateWrapper<AiExecutionTaskEntity> update = new UpdateWrapper<AiExecutionTaskEntity>()
            .set("status", status)
            .set("phase", phase)
            .set("progress", progress)
            .set("retryable", "FAILED".equals(status))
            .set("claim_token", null)
            .set("claim_expires_at", null)
            .set("next_run_at", null)
            .set("updated_at", LocalDateTime.now())
            .eq("id", episode.getExecutionId());
        if ("RUNNING".equals(status)) update.set("started_at", LocalDateTime.now());
        if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) update.set("completed_at", LocalDateTime.now());
        if (resultType != null) update.set("result_type", resultType).set("result_id", resultId);
        executionTaskMapper.update(null, update);
    }

    private Integer persistedExecutionProgress(VideoDecompositionEpisodeEntity episode) {
        AiExecutionTaskEntity execution = episode.getExecutionId() == null
            ? null : executionTaskMapper.selectById(episode.getExecutionId());
        return execution == null ? null : execution.progress;
    }

    private int terminalProgress(VideoDecompositionEpisodeEntity episode) {
        Integer progress = persistedExecutionProgress(episode);
        return progress == null ? 0 : Math.max(0, Math.min(99, progress));
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
