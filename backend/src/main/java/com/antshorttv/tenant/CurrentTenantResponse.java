package com.antshorttv.tenant;

public record CurrentTenantResponse(
    Long userId,
    Long tenantId,
    Long memberId,
    String memberType
) {
}
