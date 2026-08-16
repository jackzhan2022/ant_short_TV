package com.antshorttv.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
    @NotBlank
    @Size(min = 2, max = 50)
    String name,

    @NotBlank
    String type,

    String logo,

    @Size(max = 2000)
    String description
) {
}
