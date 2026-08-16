package com.antshorttv.tenant;

import jakarta.validation.constraints.NotBlank;

public record UpdateTenantStatusRequest(@NotBlank String status) {
}
