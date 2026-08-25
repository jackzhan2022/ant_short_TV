package com.antshorttv.project;

import java.util.Set;

public record ProjectCapabilities(
    boolean canView,
    boolean canEdit,
    boolean canDelete,
    boolean canManageMembers,
    boolean canManageRoles
) {
    static ProjectCapabilities from(Set<String> permissions, ProjectAccessSource source) {
        boolean tenantWide = source == ProjectAccessSource.TENANT_WIDE;
        return new ProjectCapabilities(
            tenantWide ? permissions.contains("PROJECT:VIEW_ALL") : permissions.contains("PROJECT:VIEW"),
            tenantWide ? permissions.contains("PROJECT:EDIT_ALL") : permissions.contains("PROJECT:EDIT"),
            tenantWide ? permissions.contains("PROJECT:DELETE_ALL") : permissions.contains("PROJECT:DELETE"),
            permissions.contains("PROJECT_MEMBER:VIEW"),
            permissions.contains("PROJECT_ROLE:VIEW")
        );
    }
}
