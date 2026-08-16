package com.antshorttv.tenant;

public record TenantSummaryResponse(
    Long id,
    String code,
    String name,
    String type,
    String logo,
    String description,
    String status,
    String memberType,
    Long memberId
) {

    public static TenantSummaryResponse from(TenantEntity tenant, String memberType, Long memberId) {
        return new TenantSummaryResponse(
            tenant.getId(),
            tenant.getCode(),
            tenant.getName(),
            tenant.getType(),
            tenant.getLogo(),
            tenant.getDescription(),
            tenant.getStatus(),
            memberType,
            memberId
        );
    }
}
