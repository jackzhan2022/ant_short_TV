package com.antshorttv.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.antshorttv.project.*;
import com.antshorttv.security.TenantContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuProjectScopeTest {
    @Test
    void tenantMenuCannotBypassProjectMembershipInBackgroundPermissionChecks() {
        RoleMapper roles = mock(RoleMapper.class);
        PermissionMapper permissions = mock(PermissionMapper.class);
        RolePermissionMapper rolePermissions = mock(RolePermissionMapper.class);
        MemberRoleMapper memberRoles = mock(MemberRoleMapper.class);
        ProjectMapper projects = mock(ProjectMapper.class);
        ProjectMemberMapper members = mock(ProjectMemberMapper.class);
        ProjectRolePermissionMapper projectPermissions = mock(ProjectRolePermissionMapper.class);
        ProjectRoleMapper projectRoles = mock(ProjectRoleMapper.class);
        RbacPermissionService service = new RbacPermissionService(roles, permissions, rolePermissions,
            memberRoles, projects, members, projectPermissions, projectRoles);
        TenantContext context = new TenantContext(1L, 2L, 3L, "MEMBER");
        MemberRoleEntity assignment = new MemberRoleEntity();
        assignment.setRoleId(4L);
        RoleEntity role = new RoleEntity();
        role.setId(4L); role.setTenantId(2L); role.setStatus("ACTIVE"); role.setProjectAccessAll(false);
        RolePermissionEntity grant = new RolePermissionEntity(); grant.setPermissionId(5L);
        PermissionEntity menu = new PermissionEntity(); menu.setId(5L); menu.setCode("MENU:PROJECTS");
        when(memberRoles.selectByMemberId(3L)).thenReturn(List.of(assignment));
        when(roles.selectBatchIds(List.of(4L))).thenReturn(List.of(role));
        when(rolePermissions.selectByRoleIds(java.util.Set.of(4L))).thenReturn(List.of(grant));
        when(permissions.selectBatchIds(List.of(5L))).thenReturn(List.of(menu));
        ProjectEntity project = new ProjectEntity(); project.id = 6L; project.tenantId = 2L;
        when(projects.selectByTenantIdAndId(2L, 6L)).thenReturn(project);
        assertThat(service.hasPermission(context, "STORYBOARD:AI_BREAKDOWN")).isTrue();
        assertThat(service.hasPermission(context, "STORYBOARD:AI_BREAKDOWN", 6L)).isFalse();
        role.setProjectAccessAll(true);
        assertThat(service.hasPermission(context, "STORYBOARD:AI_BREAKDOWN", 6L)).isTrue();
        assertThat(service.hasPermission(context, "STORYBOARD:AI_BREAKDOWN", 99L)).isFalse();
    }
}
