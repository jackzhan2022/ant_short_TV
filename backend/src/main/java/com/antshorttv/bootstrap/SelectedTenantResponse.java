package com.antshorttv.bootstrap;

import com.antshorttv.tenant.TenantSummaryResponse;
import java.util.Set;

public record SelectedTenantResponse(
    TenantSummaryResponse tenant,
    TenantMembershipResponse membership,
    Set<String> roles,
    Set<String> permissions
) {
}
