package com.antshorttv.security;

public record TenantContext(
    Long userId,
    Long tenantId,
    Long memberId,
    String memberType
) {
}
