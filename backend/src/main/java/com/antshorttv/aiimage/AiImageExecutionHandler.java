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
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionAttemptEntity;
import com.antshorttv.execution.AiExecutionAttemptMapper;
import com.antshorttv.execution.AiExecutionClaimLostException;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionDeferredException;
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
import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.RegisteredMediaObject;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AiImageExecutionHandler extends AiExecutionHandler {
    private static final Pattern INTERNAL_RESULT_DOWNLOAD = Pattern.compile(
        "^/api/projects/(\\d+)/ai-image-results/(\\d+)/(?:download|display|thumbnail)$");
    private final AiImageTaskMapper taskMapper;
    private final AiImageResultMapper resultMapper;
    private final AiImageStorageService storageService;
    private final ImageDisplayRenditionService imageRenditions;
    private final AiImageRenditionPublicationService renditionPublication;
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
        ImageDisplayRenditionService imageRenditions,
        AiImageRenditionPublicationService renditionPublication,
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
        this.imageRenditions = imageRenditions;
        this.renditionPublication = renditionPublication;
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
        List<AiImageResultEntity> existingResults = resultMapper.selectByTask(task.getId());
        if (AiImageTaskStatus.SUCCESS.name().equals(task.getStatus())) {
            requirePublishedResultSet(task, existingResults);
            requireActiveClaim(context);
            return new AiExecutionHandlerResult("AI_IMAGE_TASK", task.getId());
        }
        if (AiImageTaskStatus.RENDERING.name().equals(task.getStatus())) {
            requireCompleteResultSet(task, existingResults);
            return reconcileRenditions(context, task, existingResults);
        }
        if (AiImageTaskStatus.SETTLING.name().equals(task.getStatus())) {
            requireCompleteResultSet(task, existingResults);
            return settleAndAwaitRenditions(context, task, providerCompletion(context, task));
        }
        if (!existingResults.isEmpty()) {
            if (!completeResultSet(task, existingResults)) {
                discardUnpublishedResults(
                    task, existingResults.stream().map(AiImageResultEntity::getId).toList()
                );
            } else {
                ProviderCompletion provider = providerCompletion(context, task);
                markDomainSettling(task, provider.aiCallLogId());
                return settleAndAwaitRenditions(context, task, provider);
            }
        }
        java.util.List<Long> createdResultIds = new java.util.ArrayList<>();
        boolean providerPhaseComplete = false;
        ProviderCompletion completedProvider = null;
        authorizeProviderInvocation(context, task);
        markDomainRunning(task);
        try {
            AiInvocationResult<AiImageResponse> result = invocationService.invokeImage(
                invocationRequest(context, task)
            );
            markAttempt(context, result);
            requireActiveClaim(context);
            AiImageResponse response = result.response();
            List<String> imageUrls = response.imageUrls() == null ? List.of() : response.imageUrls().stream()
                .filter(url -> url != null && !url.isBlank()).toList();
            int imageCount = imageUrls.size();
            if (imageCount == 0) {
                settleFailure(context, result.aiCallLogId());
                throw new IllegalStateException("服务商未返回有效图片，等待结算复核。");
            }
            task.setImageCount(imageCount);
            taskMapper.updateById(task);
            // Provider work is billable even if local image storage or publication subsequently fails.
            completedProvider = new ProviderCompletion(
                context.claim().attemptId(), result.aiCallLogId(), result.resolvedModelId());
            recordUsageAndSettle(context, task, completedProvider, imageCount, false);
            for (int index = 1; index <= imageCount; index++) {
                String imageUrl = imageUrls.get(index - 1);
                createResult(context, task, index, imageUrl, createdResultIds);
            }
            requireActiveClaim(context);
            markDomainSettling(task, result.aiCallLogId());
            providerPhaseComplete = true;
            return settleAndAwaitRenditions(context, task, new ProviderCompletion(
                context.claim().attemptId(), result.aiCallLogId(), result.resolvedModelId()
            ));
        } catch (AiExecutionClaimLostException exception) {
            if (!providerPhaseComplete) discardUnpublishedResults(task, createdResultIds);
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
        } catch (RuntimeException exception) {
            if (completedProvider != null && !providerPhaseComplete) {
                recordUsageAndSettle(context, task, completedProvider, task.getImageCount());
            }
            throw exception;
        }
    }

    private void authorizeProviderInvocation(AiExecutionContext context, AiImageTaskEntity task) {
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(context.task().id);
        if (reservation == null) throw new IllegalStateException("AI 图片缺少积分预占。");
        if ("SETTLEMENT_REVIEW_REQUIRED".equals(reservation.status)) {
            throw new IllegalStateException("AI 图片等待结算复核，不能再次调用服务商。");
        }
        if ("SETTLED".equals(reservation.status) || "RELEASED".equals(reservation.status)) {
            Map<AiUsageMetric, BigDecimal> budget = new java.util.EnumMap<>(AiUsageMetric.class);
            for (String metric : AiAccountingJson.read(reservation.authorizedUsageJson).keySet()) {
                AiUsageMetric usageMetric = AiUsageMetric.valueOf(metric);
                BigDecimal quantity = switch (usageMetric) {
                    case CALL -> BigDecimal.ONE;
                    case IMAGE -> BigDecimal.valueOf(task.getImageCount());
                    case INPUT_IMAGE -> BigDecimal.valueOf(Math.max(0,
                        ReferenceImagesCodec.decode(task.getReferenceImages()).size() - 1));
                    default -> throw new IllegalStateException("AI 图片预占包含不受支持的计费指标。");
                };
                budget.put(usageMetric, quantity);
            }
            AiPointReservationEntity reopened = pointSettlementService.authorizeUsage(reservation.id, budget,
                "execution:%d:attempt:%d:image-reserve".formatted(context.task().id, context.claim().attemptId()));
            updateSettlementSummary(context.task().id, reopened);
        } else if (!"RESERVED".equals(reservation.status)) {
            throw new IllegalStateException("AI 图片积分预占状态不允许调用服务商。");
        }
    }

    private AiInvocationRequest invocationRequest(AiExecutionContext context, AiImageTaskEntity task) {
        AiExecutionTaskEntity execution = context.task();
        String prompt = task.getStyle() == null || task.getStyle().isBlank()
            ? task.getPrompt() : "画面风格：" + task.getStyle().trim() + "\n\n" + task.getPrompt();
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
                prompt, task.getNegativePrompt(), null, task.getAspectRatio(),
                task.getImageCount(), resolveReferenceImages(task)
            ))
            .requestSummary(prompt)
            .build();
    }

    private List<String> resolveReferenceImages(AiImageTaskEntity task) {
        List<String> references = new ArrayList<>();
        for (String reference : ReferenceImagesCodec.decode(task.getReferenceImages())) {
            String value = reference.trim();
            if (value.startsWith("data:") || value.startsWith("http://") || value.startsWith("https://")) {
                references.add(value);
                continue;
            }
            Matcher matcher = INTERNAL_RESULT_DOWNLOAD.matcher(value);
            if (!matcher.matches()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "参考图地址不受支持。");
            }
            Long projectId = Long.valueOf(matcher.group(1));
            Long resultId = Long.valueOf(matcher.group(2));
            AiImageResultEntity result = resultMapper.selectById(resultId);
            if (result == null || !task.getTenantId().equals(result.getTenantId())
                || !task.getProjectId().equals(projectId) || !projectId.equals(result.getProjectId())
                || !AiImageResultStatus.ACTIVE.name().equals(result.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "参考图不存在或不可用。");
            }
            try (InputStream stream = storageService.resource(result).getInputStream()) {
                references.add("data:" + result.getMimeType() + ";base64," + Base64.getEncoder().encodeToString(stream.readAllBytes()));
            } catch (Exception exception) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "参考图读取失败：" + exception.getMessage());
            }
        }
        return references;
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
        ProviderCompletion provider,
        int imageCount
    ) {
        recordUsageAndSettle(context, task, provider, imageCount, true);
    }

    private void recordUsageAndSettle(
        AiExecutionContext context, AiImageTaskEntity task, ProviderCompletion provider,
        int imageCount, boolean settle
    ) {
        LocalDateTime observedAt = LocalDateTime.now();
        AiUsageContext usageContext = new AiUsageContext(
            context.task().tenantId,
            context.task().id,
            provider.attemptId(),
            provider.aiCallLogId(),
            provider.modelId()
        );
        usageAccountingService.recordIfAbsent(AiUsageCommand.requestDerived(
            usageContext, AiUsageMetric.CALL, "1", Map.of(), observedAt
        ));
        usageAccountingService.recordIfAbsent(AiUsageCommand.resultMeasured(
            usageContext, AiUsageMetric.IMAGE, String.valueOf(imageCount), imageDimensions(task), observedAt
        ));
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(context.task().id);
        Map<AiUsageMetric, BigDecimal> actualUsage = new java.util.EnumMap<>(AiUsageMetric.class);
        actualUsage.put(AiUsageMetric.CALL, BigDecimal.ONE);
        actualUsage.put(AiUsageMetric.IMAGE, BigDecimal.valueOf(imageCount));
        if (AiAccountingJson.read(reservation.authorizedUsageJson).containsKey(AiUsageMetric.INPUT_IMAGE.name())) {
            BigDecimal inputImages = BigDecimal.valueOf(Math.max(0, ReferenceImagesCodec.decode(task.getReferenceImages()).size() - 1));
            actualUsage.put(AiUsageMetric.INPUT_IMAGE, inputImages);
            usageAccountingService.recordIfAbsent(AiUsageCommand.requestDerived(
                usageContext, AiUsageMetric.INPUT_IMAGE, inputImages.toPlainString(), imageDimensions(task), observedAt));
        }
        AiExecutionCostSummary cost = usageAccountingService.priceExecution(context.task().id, actualUsage.keySet());
        updateCostSummary(context.task().id, cost);
        if (!settle) return;

        AiPointReservationEntity settled = pointSettlementService.finalizeOutcome(
            reservation.id,
            AiSettlementOutcome.SUCCESS,
            actualUsage,
            provider.attemptId(),
            provider.aiCallLogId(),
            "execution:%d:v%d:attempt:%d:success".formatted(context.task().id, context.task().executionVersion, provider.attemptId())
        );
        if (!"SETTLED".equals(settled.status)) {
            throw new IllegalStateException("AI 图片积分结算尚未完成。");
        }
        updateSettlementSummary(context.task().id, settled);
    }

    private void settleFailure(AiExecutionContext context, Long callLogId) {
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(context.task().id);
        if (reservation == null || !"RESERVED".equals(reservation.status)) {
            return;
        }
        AiPointReservationEntity settled = pointSettlementService.finalizeOutcome(
            reservation.id,
            callLogId == null ? AiSettlementOutcome.PROVIDER_REJECTION : AiSettlementOutcome.TRANSPORT_UNKNOWN,
            Map.of(),
            context.claim().attemptId(),
            callLogId,
            "execution:%d:v%d:attempt:%d:failure".formatted(context.task().id, context.task().executionVersion, context.claim().attemptId())
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

    private void markDomainSettling(AiImageTaskEntity task, Long callLogId) {
        task.setStatus(AiImageTaskStatus.SETTLING.name());
        task.setAiCallLogId(callLogId);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void markDomainRendering(AiImageTaskEntity task) {
        task.setStatus(AiImageTaskStatus.RENDERING.name());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private AiExecutionHandlerResult settleAndAwaitRenditions(
        AiExecutionContext context,
        AiImageTaskEntity task,
        ProviderCompletion provider
    ) {
        recordUsageAndSettle(context, task, provider, task.getImageCount());
        requireActiveClaim(context);
        markDomainRendering(task);
        throw new AiExecutionDeferredException("等待 AI 图片展示版本完成处理。");
    }

    private ProviderCompletion providerCompletion(
        AiExecutionContext context,
        AiImageTaskEntity task
    ) {
        return attemptMapper.selectByExecutionId(context.task().id).stream()
            .filter(attempt -> attempt.aiCallLogId != null)
            .filter(attempt -> task.getAiCallLogId() == null
                || task.getAiCallLogId().equals(attempt.aiCallLogId))
            .max(java.util.Comparator.comparingInt(attempt -> attempt.attemptNo == null ? 0 : attempt.attemptNo))
            .map(attempt -> new ProviderCompletion(
                attempt.id,
                attempt.aiCallLogId,
                attempt.modelId == null ? task.getModelId() : attempt.modelId
            ))
            .orElseThrow(() -> new IllegalStateException("AI 图片提供方完成记录不存在。"));
    }

    private boolean completeResultSet(
        AiImageTaskEntity task,
        List<AiImageResultEntity> results
    ) {
        return task.getImageCount() != null && results.size() == task.getImageCount()
            && results.stream().allMatch(result -> result.getStoragePath() != null
                && !result.getStoragePath().isBlank()
                && result.getDisplayPath() != null && !result.getDisplayPath().isBlank()
                && result.getMimeType() != null && !result.getMimeType().isBlank()
                && result.getFileSize() != null && result.getFileSize() > 0);
    }

    private void requireCompleteResultSet(
        AiImageTaskEntity task,
        List<AiImageResultEntity> results
    ) {
        if (!completeResultSet(task, results)) {
            throw new IllegalStateException("AI 图片结果集合不完整，不能完成展示版本发布。");
        }
    }

    private void requirePublishedResultSet(
        AiImageTaskEntity task,
        List<AiImageResultEntity> results
    ) {
        if (task.getCompletedAt() == null || !completeResultSet(task, results)
            || results.stream().anyMatch(result ->
                !AiImageResultStatus.ACTIVE.name().equals(result.getStatus()))) {
            throw new IllegalStateException("已完成 AI 图片任务的结果集合不一致。");
        }
    }

    private AiExecutionHandlerResult reconcileRenditions(
        AiExecutionContext context,
        AiImageTaskEntity task,
        List<AiImageResultEntity> results
    ) {
        boolean waiting = false;
        for (AiImageResultEntity result : results) {
            RegisteredMediaObject display = imageRenditions.display(mediaIdentity(result));
            if (display != null && "FAILED".equals(display.status())) {
                imageRenditions.retryFailedDisplay(
                    mediaIdentity(result), "ai-image-result:" + result.getId()
                );
                waiting = true;
                continue;
            }
            if (display == null || !"READY".equals(display.status())) {
                waiting = true;
            }
        }
        if (waiting) {
            throw new AiExecutionDeferredException("等待 AI 图片展示版本完成处理。");
        }
        renditionPublication.publish(context, task, results);
        return new AiExecutionHandlerResult("AI_IMAGE_TASK", task.getId());
    }

    private MediaObjectIdentity mediaIdentity(AiImageResultEntity result) {
        return new MediaObjectIdentity(
            result.getTenantId(), result.getProjectId(), "AI_IMAGE_RESULT", result.getId(),
            "result-" + result.getId()
        );
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
        result.setImageUrl("");
        result.setThumbnailUrl("");
        result.setIsSelected(false);
        result.setStatus(AiImageResultStatus.PROCESSING.name());
        result.setCreatedAt(LocalDateTime.now());
        result.setUpdatedAt(result.getCreatedAt());
        resultMapper.insert(result);
        createdResultIds.add(result.getId());

        try {
            StoredImage storedImage = storageService.storeGenerated(task, result.getId(), index, providerImageUrl);
            result.setImageUrl("/api/projects/%d/ai-image-results/%d/display".formatted(task.getProjectId(), result.getId()));
            result.setThumbnailUrl("/api/projects/%d/ai-image-results/%d/thumbnail".formatted(task.getProjectId(), result.getId()));
            result.setStoragePath(storedImage.storagePath());
            result.setDisplayPath(storedImage.displayPath());
            result.setThumbnailPath(storedImage.thumbnailPath());
            result.setMimeType(storedImage.mimeType());
            result.setWidth(storedImage.width());
            result.setHeight(storedImage.height());
            result.setFileSize(storedImage.fileSize());
            result.setUpdatedAt(LocalDateTime.now());
            resultMapper.updateById(result);
        } catch (RuntimeException exception) {
            imageRenditions.retire(mediaIdentity(result));
            resultMapper.deleteById(result.getId());
            createdResultIds.remove(result.getId());
            throw exception;
        }
        requireActiveClaim(context);
    }

    private void discardUnpublishedResults(AiImageTaskEntity task, java.util.List<Long> resultIds) {
        for (Long resultId : resultIds) {
            AiImageResultEntity result = resultMapper.selectById(resultId);
            if (result != null) imageRenditions.retire(mediaIdentity(result));
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

    private record ProviderCompletion(Long attemptId, Long aiCallLogId, Long modelId) {
    }
}
