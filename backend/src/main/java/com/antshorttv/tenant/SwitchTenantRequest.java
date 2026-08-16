package com.antshorttv.tenant;

import jakarta.validation.constraints.NotNull;

public record SwitchTenantRequest(@NotNull Long tenantId) {
}
