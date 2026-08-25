package com.antshorttv.project;

import com.antshorttv.security.TenantContext;
import java.util.Set;

public record ProjectAccessContext(
    TenantContext tenant,
    ProjectEntity project,
    ProjectAccessSource source,
    ProjectMemberEntity projectMember,
    ProjectRoleEntity projectRole,
    Set<String> effectivePermissions,
    ProjectCapabilities capabilities
) {
}
