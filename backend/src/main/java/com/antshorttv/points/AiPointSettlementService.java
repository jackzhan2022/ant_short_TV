package com.antshorttv.points;

import com.antshorttv.accounting.AiAccountingJson;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.accounting.AiModelPointPriceComponentEntity;
import com.antshorttv.accounting.AiModelPointPriceComponentMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiPointSettlementService {
    private static final int POINT_SCALE = 8;

    private final JdbcTemplate jdbc;
    private final AiPointPolicyVersionMapper policyVersionMapper;
    private final AiPointPolicyComponentMapper policyComponentMapper;
    private final AiPointReservationMapper reservationMapper;
    private final AiPointLedgerMapper ledgerMapper;
    private final PointAccountingService accountingService;
    private final ObjectMapper objectMapper;
    private final AiModelPointPriceComponentMapper modelPointPriceComponentMapper;

    public AiPointSettlementService(
        JdbcTemplate jdbc,
        AiPointPolicyVersionMapper policyVersionMapper,
        AiPointPolicyComponentMapper policyComponentMapper,
        AiPointReservationMapper reservationMapper,
        AiPointLedgerMapper ledgerMapper,
        PointAccountingService accountingService,
        ObjectMapper objectMapper,
        AiModelPointPriceComponentMapper modelPointPriceComponentMapper
    ) {
        this.jdbc = jdbc;
        this.policyVersionMapper = policyVersionMapper;
        this.policyComponentMapper = policyComponentMapper;
        this.reservationMapper = reservationMapper;
        this.ledgerMapper = ledgerMapper;
        this.accountingService = accountingService;
        this.objectMapper = objectMapper;
        this.modelPointPriceComponentMapper = modelPointPriceComponentMapper;
    }

    public boolean usesTokenPricing(Long reservationId) {
        AiPointReservationEntity reservation = requireReservation(reservationId);
        return reservation.pointPriceVersionId != null
            && matchedModelComponents(reservation.pointPriceVersionId,
                AiAccountingJson.read(reservation.dimensionsJson)).stream()
                .anyMatch(component -> component.pointRate.signum() > 0
                    && ("INPUT_TOKEN".equals(component.metric) || "OUTPUT_TOKEN".equals(component.metric)));
    }

    @Transactional
    public AiPointReservationEntity authorizeUsage(Long reservationId,
        Map<AiUsageMetric, BigDecimal> additionalUsage, String idempotencyKey) {
        AiPointReservationEntity reservation = requireReservationForUpdate(reservationId);
        if (ledgerExists(reservation, idempotencyKey + ":authorize")) return reservation;
        if (!List.of("RESERVED", "SETTLED", "RELEASED").contains(reservation.status)) {
            throw new IllegalStateException("Unresolved point reservation cannot authorize another provider call.");
        }
        Map<AiUsageMetric, BigDecimal> aggregate = new EnumMap<>(AiUsageMetric.class);
        try {
            if (reservation.authorizedUsageJson != null) aggregate.putAll(objectMapper.readValue(
                reservation.authorizedUsageJson, new TypeReference<Map<AiUsageMetric, BigDecimal>>() {}));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid authorized usage.", exception);
        }
        additionalUsage.forEach((metric, quantity) -> {
            if (quantity == null || quantity.signum() < 0) throw new IllegalArgumentException("Invalid usage quantity.");
            aggregate.merge(metric, quantity, BigDecimal::add);
        });
        BigDecimal extra = reservation.pointPriceVersionId == null
            ? calculate(reservation.policyVersionId, additionalUsage, AiAccountingJson.read(reservation.dimensionsJson))
            : calculateModelPointPrice(reservation.pointPriceVersionId, additionalUsage,
                AiAccountingJson.read(reservation.dimensionsJson))
                .multiply(reservation.discountRate == null ? BigDecimal.ONE : reservation.discountRate)
                .setScale(POINT_SCALE, RoundingMode.HALF_UP);
        // Each call adds only its own budget; released unused budgets from prior calls stay released.
        if (extra.signum() > 0 && !reserveFunds(reservation, extra)) {
            throw new BusinessException(ErrorCode.TEAM_POINTS_INSUFFICIENT, "团队积分不足，无法预占本次模型调用预算。");
        }
        reservation.reservedPoints = reservation.reservedPoints.add(extra);
        reservation.authorizedUsageJson = writeJson(aggregate);
        reservation.status = "RESERVED";
        reservation.updatedAt = LocalDateTime.now();
        reservationMapper.updateById(reservation);
        recordLedger(reservation, "INCREMENTAL_RESERVE", extra, null, null, idempotencyKey + ":authorize");
        return reservation;
    }

    @Transactional
    public AiPointReservationEntity settleCumulative(Long reservationId,
        Map<AiUsageMetric, BigDecimal> usage, Long attemptId, Long callLogId, String key) {
        return settleInternal(reservationId, usage, attemptId, callLogId, key, true);
    }

    @Transactional
    public AiPointReservationEntity finalizeCumulativeOutcome(Long reservationId, AiSettlementOutcome outcome,
        Map<AiUsageMetric, BigDecimal> usage, Long attemptId, Long callLogId, String key) {
        return finalizeOutcomeInternal(reservationId, outcome, usage, attemptId, callLogId, key, true);
    }

    @Transactional
    public AiPointReservationEntity reserve(AiPointReservationCommand command) {
        AiPointReservationEntity existing = reservationMapper.selectByIdempotency(
            command.tenantId(), command.idempotencyKey()
        );
        if (existing != null) {
            if (!existing.executionId.equals(command.executionId())
                || !existing.executionVersion.equals(command.executionVersion())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "积分预占幂等键已用于其他执行。");
            }
            return existing;
        }
        if (command.pointPriceVersionId() == null) {
            throw new com.antshorttv.accounting.ModelBillingMissingException(
                "New point reservations require a frozen model point price version.");
        }
        AiPointPolicyVersionEntity policy = null;
        BigDecimal required = calculateModelPointPrice(
            command.pointPriceVersionId(), command.authorizedUsage(), command.dimensions());
        required = required.multiply(command.discountRate()).setScale(8, RoundingMode.HALF_UP);
        accountingService.ensureAccount(command.tenantId());
        // Resolve eligibility from the frozen price's model, never from a client supplied scene.
        boolean trialEligible = jdbc.queryForObject("""
            select count(*) from ai_model_point_price_version v join ai_model m on m.id=v.model_id
             where v.id=? and m.service_type <> 'VIDEO'
            """, Integer.class, command.pointPriceVersionId()) > 0
            && !"VIDEO".equals(command.capability());
        BigDecimal trialReserved = accountingService.reserveFunds(command.tenantId(), required, trialEligible);
        if (trialReserved == null) {
            throw new BusinessException(
                ErrorCode.TEAM_POINTS_INSUFFICIENT,
                "团队积分不足，请充值后再使用 AI 能力。"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        AiPointReservationEntity reservation = new AiPointReservationEntity();
        reservation.tenantId = command.tenantId();
        reservation.userId = command.userId();
        reservation.executionId = command.executionId();
        reservation.executionVersion = command.executionVersion();
        reservation.businessType = command.businessType();
        reservation.businessId = command.businessId();
        reservation.scene = command.scene();
        reservation.policyVersionId = policy == null ? null : policy.id;
        reservation.pointPriceVersionId = command.pointPriceVersionId();
        reservation.discountRate = command.discountRate();
        reservation.trialEligible = trialEligible;
        reservation.trialReservedPoints = trialReserved;
        reservation.trialSettledPoints = BigDecimal.ZERO;
        reservation.status = "RESERVED";
        reservation.authorizedUsageJson = writeJson(command.authorizedUsage());
        reservation.dimensionsJson = AiAccountingJson.write(command.dimensions());
        reservation.reservedPoints = required;
        reservation.settledPoints = BigDecimal.ZERO;
        reservation.releasedPoints = BigDecimal.ZERO;
        reservation.refundedPoints = BigDecimal.ZERO;
        reservation.idempotencyKey = command.idempotencyKey();
        reservation.createdAt = now;
        reservation.updatedAt = now;
        reservationMapper.insert(reservation);
        recordLedger(reservation, "RESERVE", required, null, null, command.idempotencyKey());
        return reservation;
    }

    @Transactional
    public AiPointReservationEntity reserveRetry(
        Long reservationId,
        Map<AiUsageMetric, BigDecimal> authorizedUsage,
        String idempotencyKey
    ) {
        AiPointReservationEntity reservation = requireReservationForUpdate(reservationId);
        if ("RESERVED".equals(reservation.status)) {
            return reservation;
        }
        if ("SETTLEMENT_REVIEW_REQUIRED".equals(reservation.status)) {
            throw new IllegalStateException("Reservation under settlement review cannot be retried.");
        }
        BigDecimal required = calculateModelPointPrice(
            reservation.pointPriceVersionId,
            authorizedUsage == null ? Map.of() : authorizedUsage,
            AiAccountingJson.read(reservation.dimensionsJson)
        ).multiply(reservation.discountRate == null ? BigDecimal.ONE : reservation.discountRate)
            .setScale(POINT_SCALE, RoundingMode.HALF_UP);
        if (!reserveFunds(reservation, required)) {
            throw new BusinessException(ErrorCode.TEAM_POINTS_INSUFFICIENT, "团队积分不足，请充值后再重试 AI 任务。");
        }
        reservation.reservedPoints = reservation.reservedPoints.add(required);
        reservation.status = "RESERVED";
        reservation.updatedAt = LocalDateTime.now();
        reservationMapper.updateById(reservation);
        recordLedger(reservation, "RESERVE", required, null, null, idempotencyKey);
        return reservation;
    }

    @Transactional
    public AiPointReservationEntity settle(
        Long reservationId,
        Map<AiUsageMetric, BigDecimal> actualUsage,
        Long attemptId,
        Long callLogId,
        String idempotencyKey
    ) {
        return settleInternal(reservationId, actualUsage, attemptId, callLogId, idempotencyKey, false);
    }

    private AiPointReservationEntity settleInternal(Long reservationId,
        Map<AiUsageMetric, BigDecimal> actualUsage, Long attemptId, Long callLogId,
        String idempotencyKey, boolean cumulative) {
        AiPointReservationEntity reservation = requireReservationForUpdate(reservationId);
        if ("SETTLED".equals(reservation.status) || "REFUNDED".equals(reservation.status)
            || ledgerExists(reservation, idempotencyKey + ":settle")) {
            return reservation;
        }
        Map<AiUsageMetric, BigDecimal> measured = actualUsage == null ? Map.of() : actualUsage;
        if (reservation.pointPriceVersionId != null
            && matchedModelComponents(reservation.pointPriceVersionId,
                AiAccountingJson.read(reservation.dimensionsJson)).stream()
                .anyMatch(component -> component.pointRate.signum() > 0
                    && !measured.containsKey(AiUsageMetric.valueOf(component.metric)))) {
            reservation.status = "SETTLEMENT_REVIEW_REQUIRED";
            reservation.updatedAt = LocalDateTime.now();
            reservationMapper.updateById(reservation);
            recordLedger(reservation, "SETTLEMENT_REVIEW", BigDecimal.ZERO, attemptId, callLogId, idempotencyKey + ":review");
            return reservation;
        }
        BigDecimal actual = reservation.pointPriceVersionId == null
            ? calculate(reservation.policyVersionId, actualUsage == null ? Map.of() : actualUsage,
                AiAccountingJson.read(reservation.dimensionsJson))
            : calculateModelPointPrice(reservation.pointPriceVersionId,
                actualUsage == null ? Map.of() : actualUsage,
                AiAccountingJson.read(reservation.dimensionsJson)).multiply(reservation.discountRate == null ? BigDecimal.ONE : reservation.discountRate).setScale(8, RoundingMode.HALF_UP);
        if (cumulative) actual = actual.subtract(reservation.settledPoints).max(BigDecimal.ZERO);
        BigDecimal outstanding = reservation.reservedPoints
            .subtract(reservation.settledPoints)
            .subtract(reservation.releasedPoints);
        BigDecimal overage = actual.subtract(outstanding).max(BigDecimal.ZERO);
        if (overage.signum() > 0 && !incrementalReserve(reservation, overage, idempotencyKey)) {
            reservation.status = "SETTLEMENT_REVIEW_REQUIRED";
            reservation.updatedAt = LocalDateTime.now();
            reservationMapper.updateById(reservation);
            recordLedger(reservation, "SETTLEMENT_REVIEW", BigDecimal.ZERO, attemptId, callLogId, idempotencyKey + ":review");
            return reservation;
        }

        outstanding = reservation.reservedPoints
            .subtract(reservation.settledPoints)
            .subtract(reservation.releasedPoints);
        BigDecimal released = outstanding.subtract(actual);
        BigDecimal trialConsumed = reservation.trialReservedPoints.min(actual);
        accountingService.restoreTrialFunds(reservation.tenantId, reservation.trialReservedPoints.subtract(trialConsumed));
        reservation.trialReservedPoints = BigDecimal.ZERO;
        reservation.trialSettledPoints = reservation.trialSettledPoints.add(trialConsumed);
        accountingService.settleFunds(reservation.tenantId, outstanding, released, actual);
        reservation.status = "SETTLED";
        reservation.settledPoints = reservation.settledPoints.add(actual);
        reservation.releasedPoints = reservation.releasedPoints.add(released);
        reservation.settledAt = LocalDateTime.now();
        reservation.releasedAt = released.signum() > 0 ? reservation.settledAt : null;
        reservation.updatedAt = reservation.settledAt;
        reservationMapper.updateById(reservation);
        recordLedger(reservation, "SETTLE", actual, attemptId, callLogId, idempotencyKey + ":settle");
        if (released.signum() > 0) {
            recordLedger(reservation, "RELEASE", released, attemptId, callLogId, idempotencyKey + ":release");
        }
        return reservation;
    }

    @Transactional
    public AiPointReservationEntity release(Long reservationId, String idempotencyKey) {
        AiPointReservationEntity reservation = requireReservationForUpdate(reservationId);
        return release(reservation, null, null, idempotencyKey);
    }

    private AiPointReservationEntity release(
        AiPointReservationEntity reservation,
        Long attemptId,
        Long callLogId,
        String idempotencyKey
    ) {
        if ("RELEASED".equals(reservation.status)) {
            return reservation;
        }
        if (!"RESERVED".equals(reservation.status)
            && !"SETTLEMENT_REVIEW_REQUIRED".equals(reservation.status)) {
            throw new IllegalStateException("Reservation cannot be released from " + reservation.status);
        }
        BigDecimal releasable = reservation.reservedPoints
            .subtract(reservation.settledPoints)
            .subtract(reservation.releasedPoints);
        accountingService.restoreTrialFunds(reservation.tenantId, reservation.trialReservedPoints);
        reservation.trialReservedPoints = BigDecimal.ZERO;
        accountingService.releaseFunds(reservation.tenantId, releasable);
        reservation.status = "RELEASED";
        reservation.releasedPoints = reservation.releasedPoints.add(releasable);
        reservation.releasedAt = LocalDateTime.now();
        reservation.updatedAt = reservation.releasedAt;
        reservationMapper.updateById(reservation);
        recordLedger(reservation, "RELEASE", releasable, attemptId, callLogId, idempotencyKey);
        return reservation;
    }

    @Transactional
    public AiPointReservationEntity refund(Long reservationId, String idempotencyKey) {
        AiPointReservationEntity reservation = requireReservationForUpdate(reservationId);
        if ("REFUNDED".equals(reservation.status)) {
            return reservation;
        }
        if (!"SETTLED".equals(reservation.status)) {
            throw new IllegalStateException("Only settled points can be refunded.");
        }
        BigDecimal refundable = reservation.settledPoints.subtract(reservation.refundedPoints);
        accountingService.restoreTrialFunds(reservation.tenantId, reservation.trialSettledPoints);
        reservation.trialSettledPoints = BigDecimal.ZERO;
        accountingService.refundFunds(reservation.tenantId, refundable);
        reservation.status = "REFUNDED";
        reservation.refundedPoints = reservation.refundedPoints.add(refundable);
        reservation.refundedAt = LocalDateTime.now();
        reservation.updatedAt = reservation.refundedAt;
        reservationMapper.updateById(reservation);
        recordLedger(reservation, "REFUND", refundable, null, null, idempotencyKey);
        return reservation;
    }

    @Transactional
    public AiPointReservationEntity finalizeOutcome(
        Long reservationId,
        AiSettlementOutcome outcome,
        Map<AiUsageMetric, BigDecimal> actualUsage,
        Long attemptId,
        Long callLogId,
        String idempotencyKey
    ) {
        return finalizeOutcomeInternal(reservationId, outcome, actualUsage, attemptId, callLogId, idempotencyKey, false);
    }

    private AiPointReservationEntity finalizeOutcomeInternal(Long reservationId, AiSettlementOutcome outcome,
        Map<AiUsageMetric, BigDecimal> actualUsage, Long attemptId, Long callLogId,
        String idempotencyKey, boolean cumulative) {
        AiPointReservationEntity reservation = requireReservationForUpdate(reservationId);
        if (outcome == AiSettlementOutcome.PRE_CALL_CANCELED) {
            return release(reservation, attemptId, callLogId, idempotencyKey);
        }
        if (outcome == AiSettlementOutcome.TRANSPORT_UNKNOWN) {
            if ("RESERVED".equals(reservation.status)) {
                reservation.status = "SETTLEMENT_REVIEW_REQUIRED";
                reservation.updatedAt = LocalDateTime.now();
                reservationMapper.updateById(reservation);
                recordLedger(reservation, "SETTLEMENT_REVIEW", BigDecimal.ZERO, attemptId, callLogId,
                    idempotencyKey + ":review");
            }
            return reservation;
        }
        AiPointPolicyVersionEntity policy = reservation.policyVersionId == null
            ? null : policyVersionMapper.selectById(reservation.policyVersionId);
        boolean charge = switch (outcome) {
            case SUCCESS -> true;
            case PROVIDER_REJECTION -> policy != null && Boolean.TRUE.equals(policy.chargeProviderRejection);
            case PROVIDER_BILLED_FAILURE, TIMED_OUT, BUSINESS_FAILURE -> policy == null
                || (outcome == AiSettlementOutcome.PROVIDER_BILLED_FAILURE && Boolean.TRUE.equals(policy.chargeProviderBilledFailure))
                || (outcome == AiSettlementOutcome.TIMED_OUT && Boolean.TRUE.equals(policy.chargeTimeout))
                || (outcome == AiSettlementOutcome.BUSINESS_FAILURE && Boolean.TRUE.equals(policy.chargeBusinessFailure));
            case PRE_CALL_CANCELED, TRANSPORT_UNKNOWN -> false;
        };
        return charge
            ? settleInternal(reservationId, actualUsage, attemptId, callLogId, idempotencyKey, cumulative)
            : release(reservation, attemptId, callLogId, idempotencyKey);
    }

    public AiPointReconciliation reconcile(Long tenantId) {
        accountingService.ensureAccount(tenantId);
        Map<String, Object> account = jdbc.queryForMap(
            "select balance, reserved_balance from team_point_account where tenant_id = ?",
            tenantId
        );
        BigDecimal available = decimal(account.get("balance"));
        BigDecimal reserved = decimal(account.get("reserved_balance"));
        AiPointLedgerEntity latest = ledgerMapper.selectLatest(tenantId);
        BigDecimal ledgerAvailable = latest == null ? available : latest.availableBalanceAfter;
        BigDecimal ledgerReserved = latest == null ? reserved : latest.reservedBalanceAfter;
        BigDecimal reservationReserved = jdbc.queryForObject("""
            select coalesce(sum(reserved_points - settled_points - released_points), 0)
              from ai_point_reservation
             where tenant_id = ? and status in ('RESERVED', 'SETTLEMENT_REVIEW_REQUIRED')
            """, BigDecimal.class, tenantId);
        return new AiPointReconciliation(
            tenantId,
            available,
            ledgerAvailable,
            reserved,
            ledgerReserved,
            reservationReserved,
            available.compareTo(ledgerAvailable) == 0
                && reserved.compareTo(ledgerReserved) == 0
                && reserved.compareTo(reservationReserved) == 0
        );
    }

    private boolean reserveFunds(AiPointReservationEntity reservation, BigDecimal amount) {
        BigDecimal trial = accountingService.reserveFunds(reservation.tenantId, amount,
            Boolean.TRUE.equals(reservation.trialEligible));
        if (trial == null) return false;
        reservation.trialReservedPoints = reservation.trialReservedPoints.add(trial);
        return true;
    }

    private boolean incrementalReserve(
        AiPointReservationEntity reservation,
        BigDecimal overage,
        String idempotencyKey
    ) {
        if (!reserveFunds(reservation, overage)) {
            return false;
        }
        reservation.reservedPoints = reservation.reservedPoints.add(overage);
        reservation.updatedAt = LocalDateTime.now();
        reservationMapper.updateById(reservation);
        recordLedger(reservation, "INCREMENTAL_RESERVE", overage, null, null, idempotencyKey + ":incremental");
        return true;
    }

    private BigDecimal calculate(
        Long policyVersionId,
        Map<AiUsageMetric, BigDecimal> usage,
        Map<String, String> dimensions
    ) {
        return policyComponentMapper.selectByPolicyVersion(policyVersionId).stream()
            .filter(component -> dimensionsMatch(component, dimensions))
            .map(component -> componentPoints(component, usage))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(POINT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateModelPointPrice(
        Long priceVersionId,
        Map<AiUsageMetric, BigDecimal> usage,
        Map<String, String> dimensions
    ) {
        return matchedModelComponents(priceVersionId, dimensions).stream()
            .map(component -> modelComponentPoints(component, usage))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(POINT_SCALE, RoundingMode.HALF_UP);
    }

    private List<AiModelPointPriceComponentEntity> matchedModelComponents(
        Long priceVersionId, Map<String, String> dimensions) {
        Map<AiUsageMetric, AiModelPointPriceComponentEntity> selected = new EnumMap<>(AiUsageMetric.class);
        for (AiModelPointPriceComponentEntity component : modelPointPriceComponentMapper.selectByVersion(priceVersionId)) {
            Map<String, String> filters = AiAccountingJson.read(component.dimensionsJson);
            if (!filters.entrySet().stream().allMatch(entry -> entry.getValue().equals(dimensions.get(entry.getKey())))) continue;
            AiUsageMetric metric = AiUsageMetric.valueOf(component.metric);
            AiModelPointPriceComponentEntity previous = selected.get(metric);
            if (previous == null || filters.size() > AiAccountingJson.read(previous.dimensionsJson).size()) {
                selected.put(metric, component);
            }
        }
        return List.copyOf(selected.values());
    }

    private boolean ledgerExists(AiPointReservationEntity reservation, String key) {
        return jdbc.queryForObject("select count(*) from point_ledger where tenant_id = ? and idempotency_key = ?",
            Integer.class, reservation.tenantId, key) > 0;
    }

    private AiPointReservationEntity requireReservationForUpdate(Long reservationId) {
        jdbc.queryForObject("select id from ai_point_reservation where id = ? for update", Long.class, reservationId);
        return requireReservation(reservationId);
    }

    public BigDecimal calculateModelPoints(Long priceVersionId, Map<AiUsageMetric, BigDecimal> usage, Map<String, String> dimensions) {
        return calculateModelPointPrice(priceVersionId, usage, dimensions);
    }

    private BigDecimal modelComponentPoints(
        AiModelPointPriceComponentEntity component,
        Map<AiUsageMetric, BigDecimal> usage
    ) {
        BigDecimal quantity = usage.getOrDefault(AiUsageMetric.valueOf(component.metric), BigDecimal.ZERO);
        if (quantity == null || quantity.signum() < 0) throw new IllegalArgumentException("Invalid usage quantity.");
        return quantity.divide(component.unitSize, 12, RoundingMode.HALF_UP).multiply(component.pointRate);
    }

    private BigDecimal componentPoints(
        AiPointPolicyComponentEntity component,
        Map<AiUsageMetric, BigDecimal> usage
    ) {
        BigDecimal quantity = "FIXED_EXECUTION".equals(component.metric)
            ? BigDecimal.ONE
            : usage.getOrDefault(AiUsageMetric.valueOf(component.metric), BigDecimal.ZERO);
        return quantity.divide(component.unitSize, 12, RoundingMode.HALF_UP)
            .multiply(component.pointRate);
    }

    private boolean dimensionsMatch(
        AiPointPolicyComponentEntity component,
        Map<String, String> dimensions
    ) {
        return AiAccountingJson.read(component.dimensionsJson).entrySet().stream()
            .allMatch(entry -> entry.getValue().equals(dimensions.get(entry.getKey())));
    }

    private void recordLedger(
        AiPointReservationEntity reservation,
        String entryType,
        BigDecimal amount,
        Long attemptId,
        Long callLogId,
        String idempotencyKey
    ) {
        AiPointLedgerEntity ledger = new AiPointLedgerEntity();
        ledger.tenantId = reservation.tenantId;
        ledger.userId = reservation.userId;
        ledger.executionId = reservation.executionId;
        ledger.executionVersion = reservation.executionVersion;
        ledger.businessType = reservation.businessType;
        ledger.businessId = reservation.businessId;
        ledger.reservationId = reservation.id;
        ledger.attemptId = attemptId;
        ledger.aiCallLogId = callLogId;
        ledger.policyVersionId = reservation.policyVersionId;
        ledger.entryType = entryType;
        ledger.amount = amount;
        ledger.idempotencyKey = idempotencyKey;
        ledger.createdAt = LocalDateTime.now();
        accountingService.append(ledger);
    }

    private AiPointReservationEntity requireReservation(Long reservationId) {
        AiPointReservationEntity reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new IllegalArgumentException("Point reservation not found: " + reservationId);
        }
        return reservation;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid point reservation usage.", exception);
        }
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
    }
}
