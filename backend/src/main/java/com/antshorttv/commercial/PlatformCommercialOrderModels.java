package com.antshorttv.commercial;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

record PlatformCommercialOrderQuery(
    String keyword,
    String status,
    String packageType,
    int current,
    int pageSize
) {
    private static final Set<String> STATUSES = Set.of(
        "PENDING_PAYMENT", "PAID", "ENTITLEMENT_PENDING", "COMPLETED", "CLOSED", "PAYMENT_EXCEPTION");
    private static final Set<String> PACKAGE_TYPES = Set.of("POINT_PACKAGE", "SUBSCRIPTION");

    static PlatformCommercialOrderQuery of(String keyword, String status, String packageType, Integer current, Integer pageSize) {
        String normalizedStatus = normalize(status);
        String normalizedPackageType = normalize(packageType);
        int normalizedCurrent = current == null ? 1 : current;
        int normalizedPageSize = pageSize == null ? 20 : pageSize;
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) throw validation("订单状态不正确。");
        if (normalizedPackageType != null && !PACKAGE_TYPES.contains(normalizedPackageType)) throw validation("套餐类型不正确。");
        if (normalizedCurrent < 1) throw validation("页码必须大于等于 1。");
        if (normalizedPageSize < 1 || normalizedPageSize > 100) throw validation("每页数量必须在 1 到 100 之间。");
        return new PlatformCommercialOrderQuery(blankToNull(keyword), normalizedStatus, normalizedPackageType,
            normalizedCurrent, normalizedPageSize);
    }

    private static String normalize(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}

record PlatformCommercialOrderPaymentResponse(
    String provider,
    String providerTradeNo,
    String status,
    LocalDateTime paidAt
) {}

record PlatformCommercialOrderSummaryResponse(
    Long id,
    String merchantOrderNo,
    Long tenantId,
    String tenantName,
    String tenantCode,
    Long packageVersionId,
    String packageName,
    Integer packageVersionNo,
    String packageType,
    BigDecimal amount,
    String currency,
    String status,
    LocalDateTime paidAt,
    LocalDateTime createdAt,
    PlatformCommercialOrderPaymentResponse payment
) {}

record PlatformCommercialOrderDetailResponse(
    Long id,
    String merchantOrderNo,
    Long tenantId,
    String tenantName,
    String tenantCode,
    Long packageVersionId,
    String packageName,
    Integer packageVersionNo,
    String packageType,
    BigDecimal amount,
    String currency,
    String status,
    LocalDateTime expiresAt,
    LocalDateTime paidAt,
    LocalDateTime completedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    PlatformCommercialOrderPaymentResponse payment
) {}

record PlatformCommercialOrderPageResponse(
    List<PlatformCommercialOrderSummaryResponse> records,
    long total,
    int current,
    int pageSize
) {}
