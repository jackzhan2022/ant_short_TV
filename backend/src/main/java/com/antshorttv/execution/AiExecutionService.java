package com.antshorttv.execution;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.accounting.AiModelBillingResolver;
import com.antshorttv.accounting.ModelBillingSnapshot;
import com.antshorttv.commercial.CommercialEntitlementResolver;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.points.AiPointReservationCommand;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointSettlementService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

@Service
public class AiExecutionService {
    private final AiExecutionTaskMapper taskMapper;
    private final AiPointSettlementService pointSettlementService;
    private final AiPointReservationMapper reservationMapper;
    private final AiModelBillingResolver billingResolver;
    private final CommercialEntitlementResolver entitlementResolver;

    public AiExecutionService(
        AiExecutionTaskMapper taskMapper,
        AiPointSettlementService pointSettlementService,
        AiPointReservationMapper reservationMapper,
        AiModelBillingResolver billingResolver
        , CommercialEntitlementResolver entitlementResolver
    ) {
        this.taskMapper = taskMapper;
        this.pointSettlementService = pointSettlementService;
        this.reservationMapper = reservationMapper;
        this.billingResolver = billingResolver;
        this.entitlementResolver = entitlementResolver;
    }

    @Transactional
    public AiExecutionTaskEntity reserveTechnicalRetry(Long executionId, int attemptNo) {
        AiExecutionTaskEntity task = requireTask(executionId);
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(executionId);
        if (reservation == null) {
            throw new IllegalStateException("AI execution has no point reservation.");
        }
        AiPointReservationEntity reopened = pointSettlementService.reserveRetry(
            reservation.id,
            Map.of(AiUsageMetric.CALL, BigDecimal.ONE),
            "execution:%d:v%d:retry:%d:reserve".formatted(executionId, task.executionVersion, attemptNo)
        );
        updateSettlementSummary(reopened);
        taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("status", AiExecutionStatus.PENDING.name())
            .set("phase", "VIDEO_ANALYSIS")
            .set("progress", 0)
            .set("retryable", true)
            .set("next_run_at", LocalDateTime.now())
            .set("claim_token", null)
            .set("claimed_at", null)
            .set("heartbeat_at", null)
            .set("claim_expires_at", null)
            .set("error_code", null)
            .set("error_message", null)
            .set("completed_at", null)
            .set("result_type", null)
            .set("result_id", null)
            .set("updated_at", LocalDateTime.now())
            .eq("id", executionId)
            .in("status", AiExecutionStatus.FAILED.name(), AiExecutionStatus.TIMED_OUT.name()));
        return requireTask(executionId);
    }

    @Transactional
    public AiExecutionTaskEntity createWithReservation(
        AiExecutionCreateCommand command,
        Map<AiUsageMetric, BigDecimal> authorizedUsage,
        Map<String, String> dimensions
    ) {
        AiExecutionTaskEntity existing = findByIdempotency(
            command.tenantId(), command.scene(), command.clientIdempotencyKey());
        if (existing != null) {
            return existing;
        }
        ModelBillingSnapshot billing = billingResolver.requireComplete(
            command.requestedModelId(), authorizedUsage.keySet(), dimensions, LocalDateTime.now());
        AiExecutionTaskEntity task = create(command);
        var discount = entitlementResolver.resolveGlobalDiscount(task.tenantId, LocalDateTime.now());
        task.commercialSubscriptionId = discount.subscriptionId();
        task.commercialPackageVersionId = discount.packageVersionId();
        task.discountRate = discount.discountRate();
        task.preDiscountPoints = pointSettlementService.calculateModelPoints(billing.pointVersionId(), authorizedUsage, dimensions);
        task.finalPoints = task.preDiscountPoints.multiply(task.discountRate).setScale(8, java.math.RoundingMode.HALF_UP);
        taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("cost_price_version_id", billing.costVersionId())
            .set("point_price_version_id", billing.pointVersionId())
            .set("commercial_subscription_id", task.commercialSubscriptionId)
            .set("commercial_package_version_id", task.commercialPackageVersionId)
            .set("pre_discount_points", task.preDiscountPoints)
            .set("discount_rate", task.discountRate)
            .set("final_points", task.finalPoints)
            .eq("id", task.id));
        AiPointReservationEntity reservation = pointSettlementService.reserve(new AiPointReservationCommand(
            task.tenantId,
            task.userId,
            task.id,
            task.executionVersion,
            task.scene,
            task.businessType,
            task.businessId,
            task.requestedModelId,
            task.capability,
            authorizedUsage,
            dimensions,
            billing.pointVersionId(),
            task.discountRate,
            "execution:%d:v%d:reserve".formatted(task.id, task.executionVersion)
        ));
        taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("point_settlement_status", "RESERVED")
            .set("reserved_points", reservation.reservedPoints)
            .set("updated_at", LocalDateTime.now())
            .eq("id", task.id));
        return requireTask(task.id);
    }

    @Transactional
    public AiExecutionTaskEntity create(AiExecutionCreateCommand command) {
        AiExecutionTaskEntity existing = findByIdempotency(command.tenantId(), command.scene(), command.clientIdempotencyKey());
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.tenantId = command.tenantId();
        task.userId = command.userId();
        task.projectId = command.projectId();
        task.scene = command.scene();
        task.capability = command.capability();
        task.businessType = command.businessType();
        task.businessId = command.businessId();
        task.requestedModelId = command.requestedModelId();
        task.redactedInputJson = command.redactedInputJson();
        task.status = AiExecutionStatus.PENDING.name();
        task.phase = command.initialPhase();
        task.progress = 0;
        task.executionVersion = 1;
        task.clientIdempotencyKey = command.clientIdempotencyKey();
        task.traceId = command.traceId();
        task.priority = 100;
        task.nextRunAt = now;
        task.retryable = command.retryable();
        task.usageCostStatus = "PENDING";
        task.pointSettlementStatus = "PENDING";
        task.reservedPoints = BigDecimal.ZERO;
        task.settledPoints = BigDecimal.ZERO;
        task.releasedPoints = BigDecimal.ZERO;
        task.createdAt = now;
        task.updatedAt = now;
        try {
            taskMapper.insert(task);
            return task;
        } catch (DuplicateKeyException exception) {
            AiExecutionTaskEntity raced = findByIdempotency(
                command.tenantId(),
                command.scene(),
                command.clientIdempotencyKey()
            );
            if (raced != null) {
                return raced;
            }
            throw exception;
        }
    }

    @Transactional
    public AiExecutionTaskEntity regenerate(Long sourceId, String clientIdempotencyKey, String traceId) {
        AiExecutionTaskEntity source = requireTask(sourceId);
        return createRegeneration(source, source.businessId, clientIdempotencyKey, traceId);
    }

    @Transactional
    public AiExecutionTaskEntity regenerateWithReservation(
        Long sourceId,
        Long businessId,
        Long requestedModelId,
        String clientIdempotencyKey,
        String traceId,
        Map<AiUsageMetric, BigDecimal> authorizedUsage,
        Map<String, String> dimensions
    ) {
        AiExecutionTaskEntity source = requireTask(sourceId);
        AiExecutionTaskEntity existing = findByIdempotency(
            source.tenantId, source.scene, clientIdempotencyKey);
        if (existing != null) {
            return existing;
        }
        ModelBillingSnapshot billing = billingResolver.requireComplete(
            requestedModelId, authorizedUsage.keySet(), dimensions, LocalDateTime.now());
        AiExecutionTaskEntity task = createRegeneration(source, businessId, requestedModelId, clientIdempotencyKey, traceId);
        var discount = entitlementResolver.resolveGlobalDiscount(task.tenantId, LocalDateTime.now());
        task.commercialSubscriptionId = discount.subscriptionId();
        task.commercialPackageVersionId = discount.packageVersionId();
        task.discountRate = discount.discountRate();
        task.preDiscountPoints = pointSettlementService.calculateModelPoints(billing.pointVersionId(), authorizedUsage, dimensions);
        task.finalPoints = task.preDiscountPoints.multiply(task.discountRate).setScale(8, java.math.RoundingMode.HALF_UP);
        taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("cost_price_version_id", billing.costVersionId())
            .set("point_price_version_id", billing.pointVersionId())
            .set("commercial_subscription_id", task.commercialSubscriptionId)
            .set("commercial_package_version_id", task.commercialPackageVersionId)
            .set("pre_discount_points", task.preDiscountPoints)
            .set("discount_rate", task.discountRate)
            .set("final_points", task.finalPoints)
            .eq("id", task.id));
        AiPointReservationEntity reservation = pointSettlementService.reserve(new AiPointReservationCommand(
            task.tenantId,
            task.userId,
            task.id,
            task.executionVersion,
            task.scene,
            task.businessType,
            task.businessId,
            task.requestedModelId,
            task.capability,
            authorizedUsage,
            dimensions,
            billing.pointVersionId(),
            task.discountRate,
            "execution:%d:v%d:reserve".formatted(task.id, task.executionVersion)
        ));
        taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("point_settlement_status", "RESERVED")
            .set("reserved_points", reservation.reservedPoints)
            .set("updated_at", LocalDateTime.now())
            .eq("id", task.id));
        return requireTask(task.id);
    }

    private AiExecutionTaskEntity createRegeneration(
        AiExecutionTaskEntity source,
        Long businessId,
        String clientIdempotencyKey,
        String traceId
    ) {
        return createRegeneration(source, businessId, source.requestedModelId, clientIdempotencyKey, traceId);
    }

    private AiExecutionTaskEntity createRegeneration(
        AiExecutionTaskEntity source,
        Long businessId,
        Long requestedModelId,
        String clientIdempotencyKey,
        String traceId
    ) {
        if (!AiExecutionStatus.SUCCEEDED.name().equals(source.status)) {
            throw invalidStatus("Only a succeeded execution can be regenerated.");
        }
        AiExecutionTaskEntity existing = findByIdempotency(source.tenantId, source.scene, clientIdempotencyKey);
        if (existing != null) {
            return existing;
        }
        Long rootId = source.rootExecutionId == null ? source.id : source.rootExecutionId;
        taskMapper.selectOne(new QueryWrapper<AiExecutionTaskEntity>()
            .eq("id", rootId)
            .last("for update"));
        AiExecutionTaskEntity latest = taskMapper.selectOne(new QueryWrapper<AiExecutionTaskEntity>()
            .and(wrapper -> wrapper.eq("id", rootId).or().eq("root_execution_id", rootId))
            .orderByDesc("execution_version")
            .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        AiExecutionTaskEntity task = new AiExecutionTaskEntity();
        task.tenantId = source.tenantId;
        task.userId = source.userId;
        task.projectId = source.projectId;
        task.scene = source.scene;
        task.capability = source.capability;
        task.businessType = source.businessType;
        task.businessId = businessId;
        task.requestedModelId = requestedModelId;
        task.redactedInputJson = source.redactedInputJson;
        task.status = AiExecutionStatus.PENDING.name();
        task.phase = source.phase;
        task.progress = 0;
        task.executionVersion = latest.executionVersion + 1;
        task.sourceExecutionId = source.id;
        task.rootExecutionId = rootId;
        task.clientIdempotencyKey = clientIdempotencyKey;
        task.traceId = traceId;
        task.priority = source.priority;
        task.nextRunAt = now;
        task.retryable = source.retryable;
        task.usageCostStatus = "PENDING";
        task.pointSettlementStatus = "PENDING";
        task.reservedPoints = BigDecimal.ZERO;
        task.settledPoints = BigDecimal.ZERO;
        task.releasedPoints = BigDecimal.ZERO;
        task.createdAt = now;
        task.updatedAt = now;
        taskMapper.insert(task);
        return task;
    }

    @Transactional
    public AiExecutionTaskEntity cancel(Long id) {
        return cancelWithDisposition(id).task();
    }

    @Transactional
    public AiExecutionCancellation cancelWithDisposition(Long id) {
        requireTask(id);
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<AiExecutionTaskEntity> cancellation = new UpdateWrapper<AiExecutionTaskEntity>()
            .set("status", AiExecutionStatus.CANCELED.name())
            .set("claim_token", null)
            .set("claim_expires_at", null)
            .set("canceled_at", now)
            .set("completed_at", now)
            .set("updated_at", now)
            .eq("id", id);
        int pending = taskMapper.update(null, cancellation.clone()
            .eq("status", AiExecutionStatus.PENDING.name()));
        if (pending == 1) {
            return new AiExecutionCancellation(requireTask(id), true);
        }
        int running = taskMapper.update(null, cancellation
            .eq("status", AiExecutionStatus.RUNNING.name()));
        if (running == 1) {
            return new AiExecutionCancellation(requireTask(id), false);
        }
        AiExecutionTaskEntity latest = requireTask(id);
        throw invalidStatus("Execution cannot be canceled from status " + latest.status);
    }

    public record AiExecutionCancellation(AiExecutionTaskEntity task, boolean beforeProviderCall) {}

    @Transactional
    public AiExecutionTaskEntity retry(Long id) {
        AiExecutionTaskEntity task = requireTask(id);
        if (!AiExecutionStatus.FAILED.name().equals(task.status)
            && !AiExecutionStatus.TIMED_OUT.name().equals(task.status)) {
            throw invalidStatus("Execution cannot be retried from status " + task.status);
        }
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("status", AiExecutionStatus.PENDING.name())
            .set("progress", 0)
            .set("next_run_at", now)
            .set("error_code", null)
            .set("error_message", null)
            .set("completed_at", null)
            .set("updated_at", now)
            .eq("id", id)
            .in("status", AiExecutionStatus.FAILED.name(), AiExecutionStatus.TIMED_OUT.name()));
        return requireTask(id);
    }

    public AiExecutionTaskEntity requireTask(Long id) {
        AiExecutionTaskEntity task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_NOT_FOUND, "AI execution not found: " + id);
        }
        return task;
    }

    @Transactional
    public AiExecutionTaskEntity updateSettlementSummary(AiPointReservationEntity reservation) {
        taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("point_settlement_status", reservation.status)
            .set("reserved_points", reservation.reservedPoints)
            .set("settled_points", reservation.settledPoints)
            .set("released_points", reservation.releasedPoints)
            .set("updated_at", LocalDateTime.now())
            .eq("id", reservation.executionId));
        return requireTask(reservation.executionId);
    }

    private BusinessException invalidStatus(String message) {
        return new BusinessException(ErrorCode.AI_EXECUTION_STATUS_INVALID, message);
    }

    private AiExecutionTaskEntity findByIdempotency(Long tenantId, String scene, String key) {
        return taskMapper.selectOne(new QueryWrapper<AiExecutionTaskEntity>()
            .eq("tenant_id", tenantId)
            .eq("scene", scene)
            .eq("client_idempotency_key", key));
    }
}
