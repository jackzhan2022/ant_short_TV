package com.antshorttv.security;

public record TenantSelectionResult(TenantContext context, String unavailableReason) {
    public static TenantSelectionResult available(TenantContext context) {
        return new TenantSelectionResult(context, null);
    }

    public static TenantSelectionResult unavailable(String reason) {
        return new TenantSelectionResult(null, reason);
    }
}
