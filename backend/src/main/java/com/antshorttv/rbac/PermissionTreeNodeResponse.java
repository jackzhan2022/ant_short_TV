package com.antshorttv.rbac;

import java.util.List;

public record PermissionTreeNodeResponse(
    String key,
    String title,
    String resource,
    String permissionCode,
    List<PermissionTreeNodeResponse> children
) {
}
