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
        boolean view = com.antshorttv.rbac.MenuAuthorization.canReadProject(permissions);
        boolean work = com.antshorttv.rbac.MenuAuthorization.canWorkOnProject(permissions);
        return new ProjectCapabilities(view, work, work, work, work);
    }
}
