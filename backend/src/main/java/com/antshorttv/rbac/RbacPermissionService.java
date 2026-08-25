package com.antshorttv.rbac;

import com.antshorttv.member.MemberType;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.project.ProjectMemberEntity;
import com.antshorttv.project.ProjectMemberMapper;
import com.antshorttv.project.ProjectRolePermissionEntity;
import com.antshorttv.project.ProjectRolePermissionMapper;
import com.antshorttv.project.ProjectRoleEntity;
import com.antshorttv.project.ProjectRoleMapper;
import com.antshorttv.project.ProjectRoleStatus;
import com.antshorttv.security.TenantContext;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RbacPermissionService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final MemberRoleMapper memberRoleMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectRolePermissionMapper projectRolePermissionMapper;
    private final ProjectRoleMapper projectRoleMapper;

    public RbacPermissionService(
        RoleMapper roleMapper,
        PermissionMapper permissionMapper,
        RolePermissionMapper rolePermissionMapper,
        MemberRoleMapper memberRoleMapper,
        ProjectMapper projectMapper,
        ProjectMemberMapper projectMemberMapper,
        ProjectRolePermissionMapper projectRolePermissionMapper,
        ProjectRoleMapper projectRoleMapper
    ) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.memberRoleMapper = memberRoleMapper;
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectRolePermissionMapper = projectRolePermissionMapper;
        this.projectRoleMapper = projectRoleMapper;
    }

    public boolean hasPermission(TenantContext context, String permissionCode) {
        return permissionCodes(context).contains(permissionCode);
    }

    public boolean hasPermission(TenantContext context, String permissionCode, Long projectId) {
        if (hasPermission(context, permissionCode)) {
            return true;
        }
        return hasProjectPermission(context, projectId, permissionCode);
    }

    public boolean hasProjectPermission(TenantContext context, Long projectId, String permissionCode) {
        return projectPermissionCodes(context, projectId).contains(permissionCode);
    }

    public Set<String> projectPermissionCodes(TenantContext context, Long projectId) {
        if (context == null || projectId == null) {
            return Set.of();
        }
        ProjectEntity project = projectMapper.selectByTenantIdAndId(context.tenantId(), projectId);
        if (project == null) {
            return Set.of();
        }
        ProjectMemberEntity member = projectMemberMapper.selectActiveByProjectIdAndUserId(
            context.tenantId(),
            projectId,
            context.userId()
        );
        if (member == null || member.roleId == null) {
            return Set.of();
        }
        ProjectRoleEntity role = projectRoleMapper.selectByTenantProjectAndId(
            context.tenantId(),
            projectId,
            member.roleId
        );
        if (role == null || !ProjectRoleStatus.ACTIVE.name().equals(role.status)) {
            return Set.of();
        }
        List<ProjectRolePermissionEntity> rolePermissions = projectRolePermissionMapper.selectByRoleIds(
            context.tenantId(),
            projectId,
            List.of(member.roleId)
        );
        List<Long> permissionIds = rolePermissions.stream()
            .map(permission -> permission.permissionId)
            .distinct()
            .toList();
        if (permissionIds.isEmpty()) {
            return Set.of();
        }
        return permissionMapper.selectBatchIds(permissionIds)
            .stream()
            .map(PermissionEntity::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> permissionCodes(TenantContext context) {
        if (MemberType.OWNER.name().equals(context.memberType())) {
            return RbacPermissions.ALL.stream()
                .map(RbacPermissionDefinition::code)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        List<MemberRoleEntity> memberRoles = memberRoleMapper.selectByMemberId(context.memberId());
        List<Long> roleIds = memberRoles.stream().map(MemberRoleEntity::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return Set.of();
        }

        Map<Long, RoleEntity> activeRoles = roleMapper.selectBatchIds(roleIds)
            .stream()
            .filter(role -> role != null
                && context.tenantId().equals(role.getTenantId())
                && role.getDeletedAt() == null
                && RoleStatus.ACTIVE.name().equals(role.getStatus()))
            .collect(Collectors.toMap(RoleEntity::getId, Function.identity()));
        if (activeRoles.isEmpty()) {
            return Set.of();
        }

        List<Long> permissionIds = rolePermissionMapper.selectByRoleIds(activeRoles.keySet())
            .stream()
            .map(RolePermissionEntity::getPermissionId)
            .distinct()
            .toList();
        if (permissionIds.isEmpty()) {
            return Set.of();
        }

        return permissionMapper.selectBatchIds(permissionIds)
            .stream()
            .map(PermissionEntity::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> roleCodes(TenantContext context) {
        return memberRoleMapper.selectByMemberId(context.memberId()).stream()
            .map(MemberRoleEntity::getRoleId)
            .map(roleMapper::selectById)
            .filter(role -> role != null
                && context.tenantId().equals(role.getTenantId())
                && role.getDeletedAt() == null
                && RoleStatus.ACTIVE.name().equals(role.getStatus()))
            .map(RoleEntity::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
