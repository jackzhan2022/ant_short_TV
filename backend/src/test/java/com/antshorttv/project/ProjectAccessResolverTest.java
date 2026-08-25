package com.antshorttv.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.antshorttv.common.BusinessException;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import java.util.Set;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectAccessResolverTest {

    private final TenantContextResolver tenantContextResolver = mock(TenantContextResolver.class);
    private final RbacPermissionService permissionService = mock(RbacPermissionService.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectMemberMapper memberMapper = mock(ProjectMemberMapper.class);
    private final ProjectRoleMapper roleMapper = mock(ProjectRoleMapper.class);
    private final ProjectAccessResolver resolver = new ProjectAccessResolver(
        tenantContextResolver,
        permissionService,
        projectMapper,
        memberMapper,
        roleMapper
    );

    @Test
    void resolvesTenantWideAccessWithoutProjectMembership() {
        TenantContext tenant = tenantContext();
        ProjectEntity project = project();
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant);
        when(projectMapper.selectByTenantIdAndId(11L, 23L)).thenReturn(project);
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of(
            "PROJECT:VIEW_ALL", "PROJECT:EDIT_ALL"
        ));

        ProjectAccessContext access = resolver.requireView(11L, 23L);

        assertThat(access.source()).isEqualTo(ProjectAccessSource.TENANT_WIDE);
        assertThat(access.capabilities().canView()).isTrue();
        assertThat(access.capabilities().canEdit()).isTrue();
        assertThat(access.capabilities().canDelete()).isFalse();
        assertThat(access.capabilities().canManageMembers()).isFalse();
        assertThat(access.capabilities().canManageRoles()).isFalse();
        assertThat(access.projectRole()).isNull();
    }

    @Test
    void tenantWideCapabilitiesRequireTheSamePermissionsAsMemberAndRoleActions() {
        TenantContext tenant = tenantContext();
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant);
        when(projectMapper.selectByTenantIdAndId(11L, 23L)).thenReturn(project());
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of(
            "PROJECT:VIEW_ALL",
            "PROJECT:EDIT_ALL",
            "PROJECT_MEMBER:VIEW",
            "PROJECT_ROLE:VIEW"
        ));

        ProjectCapabilities capabilities = resolver.requireView(11L, 23L).capabilities();

        assertThat(capabilities.canManageMembers()).isTrue();
        assertThat(capabilities.canManageRoles()).isTrue();
    }

    @Test
    void resolvesOnlyActiveProjectMembershipWithActiveRole() {
        TenantContext tenant = tenantContext();
        ProjectEntity project = project();
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.id = 31L;
        member.roleId = 37L;
        ProjectRoleEntity role = new ProjectRoleEntity();
        role.id = 37L;
        role.status = ProjectRoleStatus.ACTIVE.name();
        role.code = "WRITER";
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant);
        when(projectMapper.selectByTenantIdAndId(11L, 23L)).thenReturn(project);
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of());
        when(memberMapper.selectActiveByProjectIdAndUserId(11L, 23L, 7L)).thenReturn(member);
        when(roleMapper.selectByTenantProjectAndId(11L, 23L, 37L)).thenReturn(role);
        when(permissionService.projectPermissionCodes(tenant, 23L)).thenReturn(Set.of("PROJECT:VIEW", "SCRIPT:EDIT"));

        ProjectAccessContext access = resolver.requireView(11L, 23L);

        assertThat(access.source()).isEqualTo(ProjectAccessSource.PROJECT_MEMBER);
        assertThat(access.projectRole()).isSameAs(role);
        assertThat(access.effectivePermissions()).containsExactlyInAnyOrder("PROJECT:VIEW", "SCRIPT:EDIT");
    }

    @Test
    void failsClosedForMissingProjectMembershipOrInactiveRole() {
        TenantContext tenant = tenantContext();
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant);
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of());

        assertThatThrownBy(() -> resolver.requireView(11L, 23L))
            .isInstanceOf(BusinessException.class);

        when(projectMapper.selectByTenantIdAndId(11L, 23L)).thenReturn(project());
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.roleId = 37L;
        when(memberMapper.selectActiveByProjectIdAndUserId(11L, 23L, 7L)).thenReturn(member);
        ProjectRoleEntity disabledRole = new ProjectRoleEntity();
        disabledRole.status = ProjectRoleStatus.DISABLED.name();
        when(roleMapper.selectByTenantProjectAndId(11L, 23L, 37L)).thenReturn(disabledRole);

        assertThatThrownBy(() -> resolver.requireView(11L, 23L))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void failsClosedForCrossTenantProjectIdAndRemovedMembership() {
        TenantContext tenant = tenantContext();
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant);
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of());

        assertThatThrownBy(() -> resolver.requireView(11L, 99L))
            .isInstanceOf(BusinessException.class);

        when(projectMapper.selectByTenantIdAndId(11L, 23L)).thenReturn(project());
        when(memberMapper.selectActiveByProjectIdAndUserId(11L, 23L, 7L)).thenReturn(null);
        assertThatThrownBy(() -> resolver.requireView(11L, 23L))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void listsAllProjectsForTenantWideViewerAndMembershipProjectsForOrdinaryMember() {
        TenantContext tenant = tenantContext();
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant);
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of("PROJECT:VIEW_ALL"));
        when(projectMapper.selectByTenantId(11L)).thenReturn(List.of(project()));

        assertThat(resolver.accessibleProjects(11L)).hasSize(1);
        verify(projectMapper).selectByTenantId(11L);

        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of());
        when(projectMapper.selectAccessibleByMember(11L, 7L)).thenReturn(List.of(project()));

        assertThat(resolver.accessibleProjects(11L)).hasSize(1);
        verify(projectMapper).selectAccessibleByMember(11L, 7L);
    }

    private TenantContext tenantContext() {
        return new TenantContext(7L, 11L, 13L, "MEMBER");
    }

    private ProjectEntity project() {
        ProjectEntity project = new ProjectEntity();
        project.id = 23L;
        project.tenantId = 11L;
        return project;
    }
}
