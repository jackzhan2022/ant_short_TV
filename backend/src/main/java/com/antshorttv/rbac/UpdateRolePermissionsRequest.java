package com.antshorttv.rbac;

import java.util.List;

public record UpdateRolePermissionsRequest(
    List<String> permissionCodes
) {
}
