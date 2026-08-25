package com.antshorttv.bootstrap;

import com.antshorttv.tenant.TenantSummaryResponse;
import com.antshorttv.user.UserProfileResponse;
import java.util.List;

public record AuthBootstrapResponse(
    UserProfileResponse user,
    BootstrapSessionResponse session,
    PlatformAccessResponse platform,
    List<TenantSummaryResponse> tenants,
    SelectedTenantResponse selectedTenant,
    String unavailableSelectionReason,
    String nextAction
) {
}
