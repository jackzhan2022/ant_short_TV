package com.antshorttv.platformtenant;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

record PlatformTenantQuery(
    String keyword,
    String status,
    String packageType,
    int current,
    int pageSize
) {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> PACKAGE_TYPES = Set.of("POINT_PACKAGE", "SUBSCRIPTION");

    static PlatformTenantQuery of(String keyword, String status, String packageType, Integer current, Integer pageSize) {
        String normalizedStatus = normalize(status);
        String normalizedPackageType = normalize(packageType);
        int normalizedCurrent = current == null ? 1 : current;
        int normalizedPageSize = pageSize == null ? 20 : pageSize;
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw validation("租户状态不正确。");
        }
        if (normalizedPackageType != null && !PACKAGE_TYPES.contains(normalizedPackageType)) {
            throw validation("套餐类型不正确。");
        }
        if (normalizedCurrent < 1) {
            throw validation("页码必须大于等于 1。");
        }
        if (normalizedPageSize < 1 || normalizedPageSize > 100) {
            throw validation("每页数量必须在 1 到 100 之间。");
        }
        return new PlatformTenantQuery(blankToNull(keyword), normalizedStatus, normalizedPackageType,
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

record PlatformTenantOwnerResponse(
    Long memberId,
    Long userId,
    String nickname,
    String mobile,
    String email
) {}

record PlatformTenantPackageResponse(
    Long subscriptionId,
    Long packageId,
    Long packageVersionId,
    String packageType,
    String name,
    String subscriptionStatus,
    LocalDateTime startsAt,
    LocalDateTime endsAt
) {}

record PlatformTenantSummaryResponse(
    Long id,
    String code,
    String name,
    String type,
    String status,
    PlatformTenantOwnerResponse owner,
    long activeMemberCount,
    BigDecimal pointBalance,
    PlatformTenantPackageResponse currentPackage,
    LocalDateTime createdAt
) {}

record PlatformTenantDetailResponse(
    Long id,
    String code,
    String name,
    String type,
    String status,
    String logo,
    String description,
    PlatformTenantOwnerResponse owner,
    long activeMemberCount,
    BigDecimal pointBalance,
    PlatformTenantPackageResponse currentPackage,
    List<PlatformTenantPackageResponse> queuedPackages,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

record PlatformTenantPageResponse(
    List<PlatformTenantSummaryResponse> records,
    long total,
    int current,
    int pageSize
) {}
