package com.antshorttv.rbac;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectAccessContext;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.project.ProjectAccessSource;
import com.antshorttv.project.ProjectCapabilities;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import org.junit.jupiter.api.Test;

class ScopedPermissionGuardTest {

    private final TenantContextResolver tenantContextResolver = mock(TenantContextResolver.class);
    private final RbacPermissionService permissionService = mock(RbacPermissionService.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectAccessResolver projectAccessResolver = mock(ProjectAccessResolver.class);

    @Test
    void tenantGuardRequiresExplicitTenantAndCurrentPermission() {
        TenantPermissionGuard guard = new TenantPermissionGuard(tenantContextResolver, permissionService);
        TenantContext context = new TenantContext(7L, 11L, 13L, "MEMBER");
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(context);
        when(permissionService.hasPermission(context, "MEMBER:VIEW")).thenReturn(true);

        guard.require(11L, "MEMBER:VIEW");

        verify(permissionService).hasPermission(context, "MEMBER:VIEW");
        assertThatThrownBy(() -> guard.require(null, "MEMBER:VIEW"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void tenantGuardDeniesInactiveMembershipAndMissingPermission() {
        TenantPermissionGuard guard = new TenantPermissionGuard(tenantContextResolver, permissionService);
        TenantContext context = new TenantContext(7L, 11L, 13L, "MEMBER");
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(context);
        when(permissionService.hasPermission(context, "MEMBER:REMOVE")).thenReturn(false);
        when(tenantContextResolver.requireActiveMember(12L))
            .thenThrow(new BusinessException(com.antshorttv.common.ErrorCode.FORBIDDEN, "成员不可用"));

        assertThatThrownBy(() -> guard.require(11L, "MEMBER:REMOVE"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guard.require(12L, "MEMBER:VIEW"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void projectGuardFailsClosedForMissingOrCrossTenantProject() {
        ProjectPermissionGuard guard = new ProjectPermissionGuard(projectAccessResolver);

        assertThatThrownBy(() -> guard.require(11L, 23L, "PROJECT:VIEW"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guard.require(11L, null, "PROJECT:VIEW"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void projectGuardUsesTenantWideOrProjectScopedPermissionOnlyAfterProjectValidation() {
        ProjectPermissionGuard guard = new ProjectPermissionGuard(projectAccessResolver);
        TenantContext context = new TenantContext(7L, 11L, 13L, "MEMBER");
        ProjectEntity project = new ProjectEntity();
        project.id = 23L;
        project.tenantId = 11L;
        ProjectAccessContext tenantWide = new ProjectAccessContext(
            context, project, ProjectAccessSource.TENANT_WIDE, null, null,
            java.util.Set.of("PROJECT:VIEW_ALL"),
            new ProjectCapabilities(true, false, false, false, false)
        );
        when(projectAccessResolver.requireView(11L, 23L)).thenReturn(tenantWide);

        guard.require(11L, 23L, "PROJECT:VIEW");

        ProjectAccessContext projectMember = new ProjectAccessContext(
            context, project, ProjectAccessSource.PROJECT_MEMBER, null, null,
            java.util.Set.of(),
            new ProjectCapabilities(false, false, false, false, false)
        );
        when(projectAccessResolver.requireView(11L, 23L)).thenReturn(projectMember);
        assertThatThrownBy(() -> guard.require(11L, 23L, "PROJECT:VIEW"))
            .isInstanceOf(BusinessException.class);
    }
}
