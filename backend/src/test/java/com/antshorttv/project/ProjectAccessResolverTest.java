package com.antshorttv.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import java.util.Set;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ProjectAccessResolverTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

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
        when(projectMapper.selectMetadataByTenantIdAndId(11L, 23L)).thenReturn(project);
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
        when(projectMapper.selectMetadataByTenantIdAndId(11L, 23L)).thenReturn(project());
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
        when(projectMapper.selectMetadataByTenantIdAndId(11L, 23L)).thenReturn(project);
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of());
        when(memberMapper.selectActiveByProjectIdAndUserId(11L, 23L, 7L)).thenReturn(member);
        when(roleMapper.selectByTenantProjectAndId(11L, 23L, 37L)).thenReturn(role);
        when(permissionService.projectRolePermissionCodes(11L, 23L, 37L)).thenReturn(Set.of("PROJECT:VIEW", "SCRIPT:EDIT"));

        ProjectAccessContext access = resolver.requireView(11L, 23L);

        assertThat(access.source()).isEqualTo(ProjectAccessSource.PROJECT_MEMBER);
        assertThat(access.projectRole()).isSameAs(role);
        assertThat(access.effectivePermissions()).containsExactlyInAnyOrder("PROJECT:VIEW", "SCRIPT:EDIT");

        when(projectMapper.selectAccessibleByMember(11L, 7L)).thenReturn(List.of(project));
        assertThat(resolver.accessibleProjectContexts(11L)).singleElement()
            .satisfies(listAccess -> assertThat(listAccess).isEqualTo(access));
        // Only the explicit access lookup above may load project metadata.
        verify(projectMapper).selectMetadataByTenantIdAndId(11L, 23L);
        verify(projectMapper).selectAccessibleByMember(11L, 7L);
        verifyNoMoreInteractions(projectMapper);
    }

    @Test
    void failsClosedForMissingProjectMembershipOrInactiveRole() {
        TenantContext tenant = tenantContext();
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant);
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of());

        assertThatThrownBy(() -> resolver.requireView(11L, 23L))
            .isInstanceOf(BusinessException.class);

        when(projectMapper.selectMetadataByTenantIdAndId(11L, 23L)).thenReturn(project());
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

        when(projectMapper.selectMetadataByTenantIdAndId(11L, 23L)).thenReturn(project());
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

    @Test
    void listsTenantWideContextsWithoutRepeatingQueriesPerProject() {
        TenantContext tenant = tenantContext();
        ProjectEntity first = project();
        ProjectEntity second = project();
        second.id = 24L;
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant);
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of("PROJECT:VIEW_ALL", "PROJECT:EDIT_ALL"));
        when(projectMapper.selectByTenantId(11L)).thenReturn(List.of(first, second));

        assertThat(resolver.accessibleProjectContexts(11L)).hasSize(2).allSatisfy(access -> {
            assertThat(access.source()).isEqualTo(ProjectAccessSource.TENANT_WIDE);
            assertThat(access.capabilities().canEdit()).isTrue();
        });
        verify(tenantContextResolver).requireActiveMember(11L);
        verify(permissionService).permissionCodes(tenant);
        verify(projectMapper).selectByTenantId(11L);
        verifyNoMoreInteractions(tenantContextResolver, permissionService, projectMapper);
        verifyNoInteractions(memberMapper, roleMapper);
    }

    @Test
    void reusesResolvedAccessWithinOneGetRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET", "/api/projects/23")));
        TenantContext tenant = tenantContext();
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant);
        when(projectMapper.selectMetadataByTenantIdAndId(11L, 23L)).thenReturn(project());
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of("PROJECT:VIEW_ALL"));

        ProjectAccessContext first = resolver.requireView(11L, 23L);
        ProjectAccessContext second = resolver.requireView(11L, 23L);

        assertThat(second).isSameAs(first);
        verify(projectMapper).selectMetadataByTenantIdAndId(11L, 23L);
        verify(permissionService).permissionCodes(tenant);
        verify(tenantContextResolver, org.mockito.Mockito.times(2)).requireActiveMember(11L);
    }

    @Test
    void rechecksProjectMembershipOnTheNextRequest() {
        TenantContext tenant = tenantContext();
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant);
        when(projectMapper.selectMetadataByTenantIdAndId(11L, 23L)).thenReturn(project());
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of("PROJECT:VIEW_ALL"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET", "/api/projects/23")));
        resolver.requireView(11L, 23L);

        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET", "/api/projects/23")));
        assertThatThrownBy(() -> resolver.requireView(11L, 23L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void doesNotReuseAnotherUsersPermissionsWithinTheSameRequest() {
        TenantContext owner = tenantContext();
        TenantContext otherUser = new TenantContext(8L, 11L, 14L, "MEMBER");
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(owner, otherUser);
        when(projectMapper.selectMetadataByTenantIdAndId(11L, 23L)).thenReturn(project());
        when(permissionService.permissionCodes(owner)).thenReturn(Set.of("PROJECT:VIEW_ALL"));
        when(permissionService.permissionCodes(otherUser)).thenReturn(Set.of());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET", "/api/projects/23")));
        resolver.requireView(11L, 23L);

        assertThatThrownBy(() -> resolver.requireView(11L, 23L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void doesNotReuseAccessForAnotherProjectWithinTheSameRequest() {
        TenantContext tenant = tenantContext();
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant);
        when(projectMapper.selectMetadataByTenantIdAndId(11L, 23L)).thenReturn(project());
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of("PROJECT:VIEW_ALL"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET", "/api/projects/23")));
        resolver.requireView(11L, 23L);

        assertThatThrownBy(() -> resolver.requireView(11L, 24L)).isInstanceOf(BusinessException.class);
        verify(projectMapper).selectMetadataByTenantIdAndId(11L, 24L);
    }

    @Test
    void doesNotReuseAccessForAnotherTenantWithinTheSameRequest() {
        TenantContext tenant = tenantContext();
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant);
        when(tenantContextResolver.requireActiveMember(12L)).thenReturn(new TenantContext(7L, 12L, 14L, "MEMBER"));
        when(projectMapper.selectMetadataByTenantIdAndId(11L, 23L)).thenReturn(project());
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of("PROJECT:VIEW_ALL"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET", "/api/projects/23")));
        resolver.requireView(11L, 23L);

        assertThatThrownBy(() -> resolver.requireView(12L, 23L)).isInstanceOf(BusinessException.class);
        verify(projectMapper).selectMetadataByTenantIdAndId(12L, 23L);
    }

    @Test
    void rechecksActiveTenantMembershipBeforeReusingAccess() {
        TenantContext tenant = tenantContext();
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant)
            .thenThrow(new BusinessException(ErrorCode.MEMBER_REMOVED, "成员已移除"));
        when(projectMapper.selectMetadataByTenantIdAndId(11L, 23L)).thenReturn(project());
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of("PROJECT:VIEW_ALL"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET", "/api/projects/23")));
        resolver.requireView(11L, 23L);

        assertThatThrownBy(() -> resolver.requireView(11L, 23L))
            .isInstanceOf(BusinessException.class).hasMessage("成员已移除");
    }

    @Test
    void doesNotCacheAccessForWriteRequests() {
        TenantContext tenant = tenantContext();
        when(tenantContextResolver.requireActiveMember(11L)).thenReturn(tenant);
        when(projectMapper.selectMetadataByTenantIdAndId(11L, 23L)).thenReturn(project());
        when(permissionService.permissionCodes(tenant)).thenReturn(Set.of("PROJECT:VIEW_ALL"), Set.of());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("PUT", "/api/projects/23")));
        resolver.requireView(11L, 23L);

        assertThatThrownBy(() -> resolver.requireView(11L, 23L)).isInstanceOf(BusinessException.class);
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
