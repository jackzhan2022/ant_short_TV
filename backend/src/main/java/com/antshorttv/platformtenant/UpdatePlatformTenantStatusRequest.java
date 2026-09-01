package com.antshorttv.platformtenant;

import jakarta.validation.constraints.NotBlank;

public record UpdatePlatformTenantStatusRequest(@NotBlank String status) {}
