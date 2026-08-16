package com.antshorttv.rbac;

import jakarta.validation.constraints.NotBlank;

public record UpdateRoleStatusRequest(
    @NotBlank
    String status
) {
}
