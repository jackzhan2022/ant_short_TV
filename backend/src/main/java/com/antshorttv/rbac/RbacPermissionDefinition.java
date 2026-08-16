package com.antshorttv.rbac;

record RbacPermissionDefinition(
    String code,
    String name,
    PermissionType type,
    String resource,
    String action
) {
}
