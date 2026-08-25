package com.antshorttv.points;

import com.antshorttv.accounting.AiAccountingJson;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
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
    private final ObjectMapper objectMapper;

    public AiPointSettlementService(
        JdbcTemplate jdbc,
        AiPointPolicyVersionMapper policyVersionMapper,
        AiPointPolicyComponentMapper policyComponentMapper,
        AiPointReservationMapper reservationMapper,
        AiPointLedgerMapper ledgerMapper,
        ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.policyVersionMapper = policyVersionMapper;
        this.policyComponentMapper = policyComponentMapper;
        this.reservationMapper = reservationMapper;
        this.ledgerMapper = ledgerMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiPointReservationEntity reserve(AiPointReservationCommand command) {
        AiPointReservationEntity existing = reservationMapper.selectByIdempotency(
            command.tenantId(), command.idempotencyKey()
        );
        if (existing != null) {
            return existing;
        }
        AiPointPolicyVersionEntity policy = resolvePolicy(command);
        BigDecimal required = calculate(policy.id, command.authorizedUsage(), command.dimensions());
        int updated = jdbc.update("""
            update team_point_account
               set balance = balance - ?,
                   reserved_balance = reserved_balance + ?,
                   total_reserved = total_reserved + ?,
                   version = version + 1,
                   updated_at = now()
             where tenant_id = ?
               and balance >= ?
            """, required, required, required, command.tenantId(), required);
        if (updated == 0) {
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
        reservation.policyVersionId = policy.id;
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
    public AiPointReservationEntity settle(
        Long reservationId,
        Map<AiUsageMetric, BigDecimal> actualUsage,
        Long attemptId,
        Long callLogId,
        String idempotencyKey
    ) {
        AiPointReservationEntity reservation = requireReservation(reservationId);
        if ("SETTLED".equals(reservation.status) || "REFUNDED".equals(reservation.status)) {
            return reservation;
        }
        BigDecimal actual = calculate(
            reservation.policyVersionId,
            actualUsage == null ? Map.of() : actualUsage,
            AiAccountingJson.read(reservation.dimensionsJson)
        );
        BigDecimal overage = actual.subtract(reservation.reservedPoints).max(BigDecimal.ZERO);
        if (overage.signum() > 0 && !incrementalReserve(reservation, overage, idempotencyKey)) {
            reservation.status = "SETTLEMENT_REVIEW_REQUIRED";
            reservation.updatedAt = LocalDateTime.now();
            reservationMapper.updateById(reservation);
            recordLedger(reservation, "SETTLEMENT_REVIEW", BigDecimal.ZERO, attemptId, callLogId, idempotencyKey + ":review");
            return reservation;
        }

        BigDecimal totalReserved = reservation.reservedPoints;
        BigDecimal released = totalReserved.subtract(actual);
        jdbc.update("""
            update team_point_account
               set reserved_balance = reserved_balance - ?,
                   balance = balance + ?,
                   total_consumed = total_consumed + ?,
                   total_released = total_released + ?,
                   version = version + 1,
                   updated_at = now()
             where tenant_id = ?
            """, totalReserved, released, actual, released, reservation.tenantId);
        reservation.status = "SETTLED";
        reservation.settledPoints = actual;
        reservation.releasedPoints = released;
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
        AiPointReservationEntity reservation = requireReservation(reservationId);
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
        jdbc.update("""
            update team_point_account
               set reserved_balance = reserved_balance - ?,
                   balance = balance + ?,
                   total_released = total_released + ?,
                   version = version + 1,
                   updated_at = now()
             where tenant_id = ?
            """, releasable, releasable, releasable, reservation.tenantId);
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
        AiPointReservationEntity reservation = requireReservation(reservationId);
        if ("REFUNDED".equals(reservation.status)) {
            return reservation;
        }
        if (!"SETTLED".equals(reservation.status)) {
            throw new IllegalStateException("Only settled points can be refunded.");
        }
        BigDecimal refundable = reservation.settledPoints.subtract(reservation.refundedPoints);
        jdbc.update("""
            update team_point_account
               set balance = balance + ?,
                   total_refunded = total_refunded + ?,
                   version = version + 1,
                   updated_at = now()
             where tenant_id = ?
            """, refundable, refundable, reservation.tenantId);
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
        AiPointReservationEntity reservation = requireReservation(reservationId);
        if (outcome == AiSettlementOutcome.PRE_CALL_CANCELED) {
            return release(reservation, attemptId, callLogId, idempotencyKey);
        }
        AiPointPolicyVersionEntity policy = policyVersionMapper.selectById(reservation.policyVersionId);
        boolean charge = switch (outcome) {
            case SUCCESS -> true;
            case PROVIDER_REJECTION -> Boolean.TRUE.equals(policy.chargeProviderRejection);
            case PROVIDER_BILLED_FAILURE -> Boolean.TRUE.equals(policy.chargeProviderBilledFailure);
            case TIMED_OUT -> Boolean.TRUE.equals(policy.chargeTimeout);
            case BUSINESS_FAILURE -> Boolean.TRUE.equals(policy.chargeBusinessFailure);
            case PRE_CALL_CANCELED -> false;
        };
        return charge
            ? settle(reservationId, actualUsage, attemptId, callLogId, idempotencyKey)
            : release(reservation, attemptId, callLogId, idempotencyKey);
    }

    public AiPointReconciliation reconcile(Long tenantId) {
        Map<String, Object> account = jdbc.queryForMap(
            "select balance, reserved_balance from team_point_account where tenant_id = ?",
            tenantId
        );
        BigDecimal available = decimal(account.get("balance"));
        BigDecimal reserved = decimal(account.get("reserved_balance"));
        AiPointLedgerEntity latest = ledgerMapper.selectLatest(tenantId);
        BigDecimal ledgerAvailable = latest == null ? available : latest.availableBalanceAfter;
        BigDecimal ledgerReserved = latest == null ? reserved : latest.reservedBalanceAfter;
        return new AiPointReconciliation(
            tenantId,
            available,
            ledgerAvailable,
            reserved,
            ledgerReserved,
            available.compareTo(ledgerAvailable) == 0 && reserved.compareTo(ledgerReserved) == 0
        );
    }

    private boolean incrementalReserve(
        AiPointReservationEntity reservation,
        BigDecimal overage,
        String idempotencyKey
    ) {
        int updated = jdbc.update("""
            update team_point_account
               set balance = balance - ?,
                   reserved_balance = reserved_balance + ?,
                   total_reserved = total_reserved + ?,
                   version = version + 1,
                   updated_at = now()
             where tenant_id = ?
               and balance >= ?
            """, overage, overage, overage, reservation.tenantId, overage);
        if (updated == 0) {
            return false;
        }
        reservation.reservedPoints = reservation.reservedPoints.add(overage);
        reservation.updatedAt = LocalDateTime.now();
        reservationMapper.updateById(reservation);
        recordLedger(reservation, "INCREMENTAL_RESERVE", overage, null, null, idempotencyKey + ":incremental");
        return true;
    }

    private AiPointPolicyVersionEntity resolvePolicy(AiPointReservationCommand command) {
        return policyVersionMapper.selectEffective(command.scene(), LocalDateTime.now()).stream()
            .filter(policy -> policy.modelId == null || policy.modelId.equals(command.modelId()))
            .filter(policy -> policy.capability == null || policy.capability.equals(command.capability()))
            .max(Comparator.comparingInt(policy ->
                (policy.modelId == null ? 0 : 2) + (policy.capability == null ? 0 : 1)))
            .orElseThrow(() -> new IllegalStateException("No effective point policy for scene " + command.scene()));
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
        Map<String, Object> account = jdbc.queryForMap(
            "select balance, reserved_balance from team_point_account where tenant_id = ?",
            reservation.tenantId
        );
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
        ledger.availableBalanceAfter = decimal(account.get("balance"));
        ledger.reservedBalanceAfter = decimal(account.get("reserved_balance"));
        ledger.idempotencyKey = idempotencyKey;
        ledger.createdAt = LocalDateTime.now();
        ledgerMapper.insert(ledger);
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
