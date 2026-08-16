package com.antshorttv.auth;

import com.antshorttv.tenant.TenantSummaryResponse;
import com.antshorttv.user.UserProfileResponse;
import java.util.List;

public record AuthSessionResponse(
    String accessToken,
    UserProfileResponse user,
    List<TenantSummaryResponse> tenants,
    String nextAction
) {
}
