package com.antshorttv.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateRoleRequest(
    @NotBlank
    @Size(max = 64)
    String name,
    @Size(max = 255)
    String description,
    List<String> permissionCodes
) {
}
