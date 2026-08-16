package com.antshorttv.rbac;

public record PermissionResponse(
    Long id,
    String code,
    String name,
    String type,
    String resource,
    String action
) {

    static PermissionResponse from(PermissionEntity permission) {
        return new PermissionResponse(
            permission.getId(),
            permission.getCode(),
            permission.getName(),
            permission.getType(),
            permission.getResource(),
            permission.getAction()
        );
    }
}
