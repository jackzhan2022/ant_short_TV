package com.antshorttv.aiimage;

import com.antshorttv.accounting.AiAccountingJson;
import com.antshorttv.accounting.AiExecutionCostSummary;
import com.antshorttv.accounting.AiUsageAccountingService;
import com.antshorttv.accounting.AiUsageCommand;
import com.antshorttv.accounting.AiUsageContext;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.ai.AiImageRequest;
import com.antshorttv.ai.AiImageResponse;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.execution.AiExecutionAttemptEntity;
import com.antshorttv.execution.AiExecutionAttemptMapper;
import com.antshorttv.execution.AiExecutionClaimLostException;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionHandler;
import com.antshorttv.execution.AiExecutionHandlerResult;
import com.antshorttv.execution.AiExecutionRetryPolicy;
import com.antshorttv.execution.AiExecutionStatus;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.points.AiSettlementOutcome;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.antshorttv.script.AssetVisualVariantService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AiImageExecutionHandler extends AiExecutionHandler {
    private final AiImageTaskMapper taskMapper;
    private final AiImageResultMapper resultMapper;
    private final AiImageStorageService storageService;
    private final AiInvocationService invocationService;
    private final AiExecutionTaskMapper executionTaskMapper;
    private final AiExecutionAttemptMapper attemptMapper;
    private final AiUsageAccountingService usageAccountingService;
    private final AiPointReservationMapper reservationMapper;
    private final AiPointSettlementService pointSettlementService;
    private final ObjectMapper objectMapper;
    private final AssetVisualVariantService assetVisualVariantService;

    public AiImageExecutionHandler(
        AiImageTaskMapper taskMapper,
        AiImageResultMapper resultMapper,
        AiImageStorageService storageService,
        AiInvocationService invocationService,
        AiExecutionTaskMapper executionTaskMapper,
        AiExecutionAttemptMapper attemptMapper,
        AiUsageAccountingService usageAccountingService,
        AiPointReservationMapper reservationMapper,
        AiPointSettlementService pointSettlementService,
        ObjectMapper objectMapper,
        AssetVisualVariantService assetVisualVariantService
    ) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.storageService = storageService;
        this.invocationService = invocationService;
        this.executionTaskMapper = executionTaskMapper;
        this.attemptMapper = attemptMapper;
        this.usageAccountingService = usageAccountingService;
        this.reservationMapper = reservationMapper;
        this.pointSettlementService = pointSettlementService;
        this.objectMapper = objectMapper;
        this.assetVisualVariantService = assetVisualVariantService;
    }

    @Override
    public String scene() {
        return "ai_image_generate";
    }

    @Override
    public AiExecutionRetryPolicy retryPolicy() {
        return new AiExecutionRetryPolicy(3, Duration.ofSeconds(5));
    }

    @Override
    public void validate(AiExecutionTaskEntity execution) {
        AiImageTaskEntity task = taskMapper.selectById(execution.businessId);
        if (task == null || !execution.id.equals(task.getExecutionId())) {
            throw new IllegalStateException("Image task is not linked to execution " + execution.id);
        }
    }

    @Override
    public AiExecutionHandlerResult execute(AiExecutionContext context) {
        AiExecutionTaskEntity execution = context.task();
        AiImageTaskEntity task = taskMapper.selectById(execution.businessId);
        java.util.List<Long> createdResultIds = new java.util.ArrayList<>();
        markDomainRunning(task);
        try {
            AiInvocationResult<AiImageResponse> result = invocationService.invokeImage(
                invocationRequest(context, task)
            );
            markAttempt(context, result);
            requireActiveClaim(context);
            AiImageResponse response = result.response();
            int imageCount = response.imageUrls() == null ? 0 : response.imageUrls().size();
            for (int index = 1; index <= task.getImageCount(); index++) {
                String imageUrl = imageCount >= index ? response.imageUrls().get(index - 1) : null;
                createResult(context, task, index, imageUrl, createdResultIds);
            }
            recordUsageAndSettle(context, task, result, task.getImageCount());
            requireActiveClaim(context);
            markDomainSucceeded(task, result.aiCallLogId());
            return new AiExecutionHandlerResult("AI_IMAGE_TASK", task.getId());
        } catch (AiExecutionClaimLostException exception) {
            discardUnpublishedResults(task, createdResultIds);
            throw exception;
        } catch (AiGatewayException exception) {
            boolean owner = markDomainFailedIfClaimActive(
                context, task, exception.getMessage(), exception.getAiCallLogId());
            if (!owner) {
                discardUnpublishedResults(task, createdResultIds);
                throw new AiExecutionClaimLostException(context.task().id);
            }
            settleFailure(context, exception.getAiCallLogId());
            throw exception;
        }
    }

    private AiInvocationRequest invocationRequest(AiExecutionContext context, AiImageTaskEntity task) {
        AiExecutionTaskEntity execution = context.task();
        return AiInvocationRequest.image()
            .tenantId(execution.tenantId)
            .userId(execution.userId)
            .projectId(execution.projectId)
            .taskId(task.getId())
            .modelId(execution.requestedModelId)
            .businessSceneCode(task.getTaskType())
            .traceId(execution.traceId)
            .executionId(execution.id)
            .attemptId(context.claim().attemptId())
            .executionVersion(execution.executionVersion)
            .phase(context.claim().phase())
            .idempotencyKey("execution:%d:v%d:%s".formatted(
                execution.id, execution.executionVersion, context.claim().phase()))
            .imageRequest(new AiImageRequest(
                task.getPrompt(), task.getNegativePrompt(), null, task.getAspectRatio(),
                task.getImageCount(), ReferenceImagesCodec.decode(task.getReferenceImages())
            ))
            .requestSummary(task.getPrompt())
            .build();
    }

    private void markAttempt(AiExecutionContext context, AiInvocationResult<AiImageResponse> result) {
        attemptMapper.update(null, new UpdateWrapper<AiExecutionAttemptEntity>()
            .set("provider_contacted", true)
            .set("provider_contacted_at", LocalDateTime.now())
            .set("provider_id", result.providerId())
            .set("model_id", result.resolvedModelId())
            .set("provider_request_id", result.providerRequestId())
            .set("ai_call_log_id", result.aiCallLogId())
            .set("transport_outcome", result.transportOutcome())
            .set("business_outcome", result.businessOutcome())
            .eq("id", context.claim().attemptId()));
    }

    private void recordUsageAndSettle(
        AiExecutionContext context,
        AiImageTaskEntity task,
        AiInvocationResult<AiImageResponse> result,
        int imageCount
    ) {
        LocalDateTime observedAt = LocalDateTime.now();
        AiUsageContext usageContext = new AiUsageContext(
            context.task().tenantId,
            context.task().id,
            context.claim().attemptId(),
            result.aiCallLogId(),
            result.resolvedModelId()
        );
        usageAccountingService.record(AiUsageCommand.requestDerived(
            usageContext, AiUsageMetric.CALL, "1", Map.of(), observedAt
        ));
        usageAccountingService.record(AiUsageCommand.resultMeasured(
            usageContext, AiUsageMetric.IMAGE, String.valueOf(imageCount), imageDimensions(task), observedAt
        ));
        AiExecutionCostSummary cost = usageAccountingService.priceExecution(
            context.task().id,
            Set.of(AiUsageMetric.CALL, AiUsageMetric.IMAGE)
        );
        updateCostSummary(context.task().id, cost);

        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(context.task().id);
        AiPointReservationEntity settled = pointSettlementService.finalizeOutcome(
            reservation.id,
            AiSettlementOutcome.SUCCESS,
            Map.of(
                AiUsageMetric.CALL, BigDecimal.ONE,
                AiUsageMetric.IMAGE, BigDecimal.valueOf(imageCount)
            ),
            context.claim().attemptId(),
            result.aiCallLogId(),
            "execution:%d:v%d:success".formatted(context.task().id, context.task().executionVersion)
        );
        updateSettlementSummary(context.task().id, settled);
    }

    private void settleFailure(AiExecutionContext context, Long callLogId) {
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(context.task().id);
        if (reservation == null || !"RESERVED".equals(reservation.status)) {
            return;
        }
        AiPointReservationEntity settled = pointSettlementService.finalizeOutcome(
            reservation.id,
            callLogId == null ? AiSettlementOutcome.PROVIDER_REJECTION : AiSettlementOutcome.PROVIDER_BILLED_FAILURE,
            Map.of(),
            context.claim().attemptId(),
            callLogId,
            "execution:%d:v%d:failure".formatted(context.task().id, context.task().executionVersion)
        );
        updateSettlementSummary(context.task().id, settled);
    }

    private void requireActiveClaim(AiExecutionContext context) {
        AiExecutionTaskEntity latest = executionTaskMapper.selectById(context.task().id);
        if (latest == null
            || !AiExecutionStatus.RUNNING.name().equals(latest.status)
            || !context.claim().claimToken().equals(latest.claimToken)) {
            throw new AiExecutionClaimLostException(context.task().id);
        }
    }

    private void markDomainRunning(AiImageTaskEntity task) {
        task.setStatus(AiImageTaskStatus.RUNNING.name());
        task.setStartedAt(LocalDateTime.now());
        task.setUpdatedAt(task.getStartedAt());
        taskMapper.updateById(task);
    }

    private void markDomainSucceeded(AiImageTaskEntity task, Long callLogId) {
        task.setStatus(AiImageTaskStatus.SUCCESS.name());
        task.setAiCallLogId(callLogId);
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(task.getCompletedAt());
        taskMapper.updateById(task);
    }

    private boolean markDomainFailedIfClaimActive(
        AiExecutionContext context, AiImageTaskEntity task, String message, Long callLogId
    ) {
        int updated = taskMapper.markFailedIfClaimActive(
            task.getId(), context.task().id, context.claim().claimToken(), message, callLogId);
        if (updated != 1) {
            return false;
        }
        if ("VISUAL_VARIANT".equals(task.getTargetType())) {
            boolean variantUpdated = assetVisualVariantService.generationFailedIfClaimActive(
                task.getTenantId(), task.getProjectId(), task.getTargetId(), task.getId(),
                "AI_IMAGE_GENERATION_FAILED", message, context.task().id, context.claim().claimToken());
            if (!variantUpdated) {
                throw new AiExecutionClaimLostException(context.task().id);
            }
        }
        return true;
    }

    private void createResult(
        AiExecutionContext context,
        AiImageTaskEntity task,
        int index,
        String providerImageUrl,
        java.util.List<Long> createdResultIds
    ) {
        requireActiveClaim(context);
        AiImageResultEntity result = new AiImageResultEntity();
        result.setTenantId(task.getTenantId());
        result.setProjectId(task.getProjectId());
        result.setTaskId(task.getId());
        result.setExecutionId(context.task().id);
        result.setTargetType(task.getTargetType());
        result.setTargetId(task.getTargetId());
        result.setImageUrl(providerImageUrl == null ? "" : providerImageUrl);
        result.setThumbnailUrl(providerImageUrl == null ? "" : providerImageUrl);
        result.setIsSelected(false);
        result.setStatus(AiImageResultStatus.ACTIVE.name());
        result.setCreatedAt(LocalDateTime.now());
        result.setUpdatedAt(result.getCreatedAt());
        resultMapper.insert(result);
        createdResultIds.add(result.getId());

        if (providerImageUrl == null || providerImageUrl.isBlank()) {
            StoredImage storedImage = storageService.createPlaceholder(task, result.getId(), index);
            String url = "/api/projects/%d/ai-image-results/%d/download".formatted(task.getProjectId(), result.getId());
            result.setImageUrl(url);
            result.setThumbnailUrl(url);
            result.setStoragePath(storedImage.storagePath());
            result.setWidth(storedImage.width());
            result.setHeight(storedImage.height());
            result.setFileSize(storedImage.fileSize());
        }
        result.setUpdatedAt(LocalDateTime.now());
        resultMapper.updateById(result);
        requireActiveClaim(context);
        if (index == 1 && "VISUAL_VARIANT".equals(task.getTargetType())) {
            boolean published = assetVisualVariantService.generationSucceededIfClaimActive(
                task.getTenantId(), task.getProjectId(), task.getTargetId(), task.getId(),
                result.getId(), result.getImageUrl(), context.task().id, context.claim().claimToken());
            if (!published) throw new AiExecutionClaimLostException(context.task().id);
        }
    }

    private void discardUnpublishedResults(AiImageTaskEntity task, java.util.List<Long> resultIds) {
        for (Long resultId : resultIds) {
            if ("VISUAL_VARIANT".equals(task.getTargetType())) {
                assetVisualVariantService.discardGeneratedResult(
                    task.getTenantId(), task.getProjectId(), task.getTargetId(), resultId);
            }
            resultMapper.deleteById(resultId);
        }
    }

    private void updateCostSummary(Long executionId, AiExecutionCostSummary cost) {
        try {
            executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
                .set("usage_cost_status", cost.status().name())
                .set("provider_cost_summary_json", objectMapper.writeValueAsString(cost.totalsByCurrency()))
                .eq("id", executionId));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist image cost summary.", exception);
        }
    }

    private void updateSettlementSummary(Long executionId, AiPointReservationEntity reservation) {
        executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("point_settlement_status", reservation.status)
            .set("reserved_points", reservation.reservedPoints)
            .set("settled_points", reservation.settledPoints)
            .set("released_points", reservation.releasedPoints)
            .eq("id", executionId));
    }

    private Map<String, String> imageDimensions(AiImageTaskEntity task) {
        return AiAccountingJson.read(AiAccountingJson.write(Map.of(
            "aspectRatio", task.getAspectRatio(),
            "quality", task.getQuality()
        )));
    }
}
