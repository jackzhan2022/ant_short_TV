package com.antshorttv.accounting;

import com.antshorttv.execution.AiExecutionResponse;
import com.antshorttv.points.AiPointLedgerEntity;
import com.antshorttv.points.AiPointPolicyComponentEntity;
import com.antshorttv.points.AiPointPolicyVersionEntity;
import com.antshorttv.points.AiPointReservationEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

record ModelPriceVersionResponse(
    Long id,
    Long modelId,
    Integer versionNo,
    String status,
    LocalDateTime effectiveFrom,
    LocalDateTime effectiveTo,
    LocalDateTime publishedAt,
    Long createdBy,
    List<ModelPriceComponentResponse> components
) {
    static ModelPriceVersionResponse from(
        AiModelPriceVersionEntity version,
        List<AiModelPriceComponentEntity> components
    ) {
        return new ModelPriceVersionResponse(
            version.id,
            version.modelId,
            version.versionNo,
            version.status,
            version.effectiveFrom,
            version.effectiveTo,
            version.publishedAt,
            version.createdBy,
            components.stream().map(ModelPriceComponentResponse::from).toList()
        );
    }
}

record ModelPriceComponentResponse(
    Long id,
    String metric,
    BigDecimal unitSize,
    BigDecimal unitPrice,
    String currency
) {
    static ModelPriceComponentResponse from(AiModelPriceComponentEntity component) {
        return new ModelPriceComponentResponse(
            component.id,
            component.metric,
            component.unitSize,
            component.unitPrice,
            component.currency
        );
    }
}

record PointPolicyVersionResponse(
    Long id,
    String scene,
    Long modelId,
    String capability,
    Integer versionNo,
    String status,
    LocalDateTime effectiveFrom,
    LocalDateTime effectiveTo,
    Boolean chargeProviderRejection,
    Boolean chargeProviderBilledFailure,
    Boolean chargeTimeout,
    Boolean chargeBusinessFailure,
    LocalDateTime publishedAt,
    List<PointPolicyComponentResponse> components
) {
    static PointPolicyVersionResponse from(
        AiPointPolicyVersionEntity version,
        List<AiPointPolicyComponentEntity> components
    ) {
        return new PointPolicyVersionResponse(
            version.id,
            version.scene,
            version.modelId,
            version.capability,
            version.versionNo,
            version.status,
            version.effectiveFrom,
            version.effectiveTo,
            version.chargeProviderRejection,
            version.chargeProviderBilledFailure,
            version.chargeTimeout,
            version.chargeBusinessFailure,
            version.publishedAt,
            components.stream().map(PointPolicyComponentResponse::from).toList()
        );
    }
}

record PointPolicyComponentResponse(
    Long id,
    String metric,
    BigDecimal unitSize,
    BigDecimal pointRate
) {
    static PointPolicyComponentResponse from(AiPointPolicyComponentEntity component) {
        return new PointPolicyComponentResponse(
            component.id,
            component.metric,
            component.unitSize,
            component.pointRate
        );
    }
}

record PlatformAiAccountingDetailResponse(
    AiExecutionResponse execution,
    List<UsageLineResponse> usageLines,
    List<UsageCostLineResponse> costLines,
    PointSettlementDetailResponse settlement
) {
}

record UsageLineResponse(
    Long id,
    Long attemptId,
    Long aiCallLogId,
    Long modelId,
    String metric,
    BigDecimal quantity,
    String unit,
    String source,
    LocalDateTime observedAt,
    Long adjustmentOfUsageLineId
) {
    static UsageLineResponse from(AiUsageLineEntity line) {
        return new UsageLineResponse(
            line.id,
            line.attemptId,
            line.aiCallLogId,
            line.modelId,
            line.metric,
            line.quantity,
            line.unit,
            line.source,
            line.observedAt,
            line.adjustmentOfUsageLineId
        );
    }
}

record UsageCostLineResponse(
    Long id,
    Long usageLineId,
    Long priceVersionId,
    Long priceComponentId,
    String metric,
    BigDecimal quantity,
    BigDecimal unitSize,
    BigDecimal unitPrice,
    String currency,
    BigDecimal rawCost,
    BigDecimal roundedCost,
    String pricingStatus,
    Long adjustmentOfCostLineId
) {
    static UsageCostLineResponse from(AiUsageCostLineEntity line) {
        return new UsageCostLineResponse(
            line.id,
            line.usageLineId,
            line.priceVersionId,
            line.priceComponentId,
            line.metric,
            line.quantity,
            line.unitSize,
            line.unitPrice,
            line.currency,
            line.rawCost,
            line.roundedCost,
            line.pricingStatus,
            line.adjustmentOfCostLineId
        );
    }
}

record PointSettlementDetailResponse(
    PointReservationResponse reservation,
    List<PointLedgerResponse> ledger
) {
}

record PointReservationResponse(
    Long id,
    Long policyVersionId,
    String status,
    BigDecimal reservedPoints,
    BigDecimal settledPoints,
    BigDecimal releasedPoints,
    BigDecimal refundedPoints,
    LocalDateTime createdAt,
    LocalDateTime settledAt,
    LocalDateTime releasedAt,
    LocalDateTime refundedAt
) {
    static PointReservationResponse from(AiPointReservationEntity reservation) {
        if (reservation == null) {
            return null;
        }
        return new PointReservationResponse(
            reservation.id,
            reservation.policyVersionId,
            reservation.status,
            reservation.reservedPoints,
            reservation.settledPoints,
            reservation.releasedPoints,
            reservation.refundedPoints,
            reservation.createdAt,
            reservation.settledAt,
            reservation.releasedAt,
            reservation.refundedAt
        );
    }
}

record PointLedgerResponse(
    Long id,
    Long attemptId,
    Long aiCallLogId,
    Long policyVersionId,
    String entryType,
    BigDecimal amount,
    BigDecimal availableBalanceAfter,
    BigDecimal reservedBalanceAfter,
    LocalDateTime createdAt
) {
    static PointLedgerResponse from(AiPointLedgerEntity ledger) {
        return new PointLedgerResponse(
            ledger.id,
            ledger.attemptId,
            ledger.aiCallLogId,
            ledger.policyVersionId,
            ledger.entryType,
            ledger.amount,
            ledger.availableBalanceAfter,
            ledger.reservedBalanceAfter,
            ledger.createdAt
        );
    }
}
