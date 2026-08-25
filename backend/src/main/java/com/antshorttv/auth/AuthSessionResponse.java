package com.antshorttv.auth;

import com.antshorttv.tenant.TenantSummaryResponse;
import com.antshorttv.user.UserProfileResponse;
import java.time.LocalDateTime;
import java.util.List;

public record AuthSessionResponse(
    UserProfileResponse user,
    List<TenantSummaryResponse> tenants,
    String nextAction,
    LocalDateTime expiresAt
) {
}
