package com.antshorttv.rbac;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.member.MemberStatus;
import com.antshorttv.member.MemberType;
import com.antshorttv.member.TenantMemberEntity;
import com.antshorttv.member.TenantMemberMapper;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.security.CurrentTenantStore;
import com.antshorttv.security.CurrentUserHolder;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RbacService {

    private static final List<String> DEFAULT_ROLE_CODES = List.of("OWNER", "ADMIN", "MEMBER");

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final MemberRoleMapper memberRoleMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final TenantContextResolver tenantContextResolver;
    private final CurrentTenantStore currentTenantStore;
    private final RbacPermissionService rbacPermissionService;
    private final OperationLogService operationLogService;

    public RbacService(
        RoleMapper roleMapper,
        PermissionMapper permissionMapper,
        RolePermissionMapper rolePermissionMapper,
        MemberRoleMapper memberRoleMapper,
        TenantMemberMapper tenantMemberMapper,
        TenantContextResolver tenantContextResolver,
        CurrentTenantStore currentTenantStore,
        RbacPermissionService rbacPermissionService,
        OperationLogService operationLogService
    ) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.memberRoleMapper = memberRoleMapper;
        this.tenantMemberMapper = tenantMemberMapper;
        this.tenantContextResolver = tenantContextResolver;
        this.currentTenantStore = currentTenantStore;
        this.rbacPermissionService = rbacPermissionService;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public List<RoleResponse> listRoles(Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        initializeTenant(tenantId);
        return roleMapper.selectByTenantId(tenantId)
            .stream()
            .map(role -> RoleResponse.from(role, memberRoleMapper.countByRoleId(role.getId())))
            .toList();
    }

    @Transactional
    public RoleResponse createRole(Long tenantId, CreateRoleRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        initializeTenant(tenantId);
        String code = validateRoleCode(request.code());
        if (DEFAULT_ROLE_CODES.contains(code) || roleMapper.selectActiveByTenantIdAndCode(tenantId, code) != null) {
            throw new BusinessException(ErrorCode.ROLE_NAME_DUPLICATE, "角色编码已存在。");
        }

        LocalDateTime now = LocalDateTime.now();
        RoleEntity role = new RoleEntity();
        role.setTenantId(tenantId);
        role.setCode(code);
        role.setName(validateRoleName(request.name()));
        role.setDescription(blankToNull(request.description()));
        role.setRoleType(RoleType.CUSTOM.name());
        role.setStatus(RoleStatus.ACTIVE.name());
        role.setIsDefault(false);
        role.setCreatedBy(context.userId());
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        roleMapper.insert(role);
        replaceRolePermissions(role, request.permissionCodes());
        operationLogService.record(context.userId(), tenantId, "CREATE_ROLE", role.getId(), OperationResult.SUCCESS, servletRequest);
        return RoleResponse.from(role, 0);
    }

    @Transactional
    public RoleResponse detail(Long tenantId, Long roleId) {
        tenantContextResolver.requireActiveMember(tenantId);
        initializeTenant(tenantId);
        RoleEntity role = requireRoleInTenant(tenantId, roleId);
        return RoleResponse.from(role, memberRoleMapper.countByRoleId(roleId));
    }

    @Transactional
    public RoleResponse updateRole(Long tenantId, Long roleId, UpdateRoleRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        initializeTenant(tenantId);
        RoleEntity role = requireRoleInTenant(tenantId, roleId);
        ensureOwnerMutable(role);
        role.setName(validateRoleName(request.name()));
        role.setDescription(blankToNull(request.description()));
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);
        replaceRolePermissions(role, request.permissionCodes());
        operationLogService.record(context.userId(), tenantId, "UPDATE_ROLE", roleId, OperationResult.SUCCESS, servletRequest);
        return RoleResponse.from(role, memberRoleMapper.countByRoleId(roleId));
    }

    @Transactional
    public void deleteRole(Long tenantId, Long roleId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        initializeTenant(tenantId);
        RoleEntity role = requireRoleInTenant(tenantId, roleId);
        if ("OWNER".equals(role.getCode())) {
            throw new BusinessException(ErrorCode.OWNER_ROLE_DELETE_BLOCKED, "Owner角色不可删除。");
        }
        if (RoleType.SYSTEM.name().equals(role.getRoleType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统角色不可删除。");
        }
        long memberCount = memberRoleMapper.countByRoleId(roleId);
        if (memberCount > 0) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE, "当前角色已分配给 %d 名成员，请先完成角色调整。".formatted(memberCount));
        }
        rolePermissionMapper.deleteByRoleId(roleId);
        roleMapper.deleteById(roleId);
        operationLogService.record(context.userId(), tenantId, "DELETE_ROLE", roleId, OperationResult.SUCCESS, servletRequest);
    }

    @Transactional
    public RoleResponse updateStatus(Long tenantId, Long roleId, UpdateRoleStatusRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        initializeTenant(tenantId);
        RoleEntity role = requireRoleInTenant(tenantId, roleId);
        ensureOwnerMutable(role);
        RoleStatus status = validateRoleStatus(request.status());
        role.setStatus(status.name());
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);
        operationLogService.record(context.userId(), tenantId, "UPDATE_ROLE_STATUS", roleId, OperationResult.SUCCESS, servletRequest);
        return RoleResponse.from(role, memberRoleMapper.countByRoleId(roleId));
    }

    @Transactional
    public List<PermissionResponse> rolePermissions(Long tenantId, Long roleId) {
        tenantContextResolver.requireActiveMember(tenantId);
        initializeTenant(tenantId);
        requireRoleInTenant(tenantId, roleId);
        List<Long> permissionIds = rolePermissionMapper.selectByRoleId(roleId)
            .stream()
            .map(RolePermissionEntity::getPermissionId)
            .toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectBatchIds(permissionIds)
            .stream()
            .sorted(Comparator.comparing(PermissionEntity::getResource).thenComparing(PermissionEntity::getAction))
            .map(PermissionResponse::from)
            .toList();
    }

    @Transactional
    public List<PermissionResponse> updateRolePermissions(
        Long tenantId,
        Long roleId,
        UpdateRolePermissionsRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        initializeTenant(tenantId);
        RoleEntity role = requireRoleInTenant(tenantId, roleId);
        ensureOwnerMutable(role);
        replaceRolePermissions(role, request.permissionCodes());
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);
        operationLogService.record(context.userId(), tenantId, "UPDATE_ROLE_PERMISSIONS", roleId, OperationResult.SUCCESS, servletRequest);
        return rolePermissions(tenantId, roleId);
    }

    @Transactional
    public List<RoleResponse> memberRoles(Long tenantId, Long memberId) {
        tenantContextResolver.requireActiveMember(tenantId);
        initializeTenant(tenantId);
        requireMemberInTenant(tenantId, memberId);
        return rolesForMember(memberId)
            .stream()
            .map(role -> RoleResponse.from(role, memberRoleMapper.countByRoleId(role.getId())))
            .toList();
    }

    @Transactional
    public List<RoleResponse> updateMemberRoles(
        Long tenantId,
        Long memberId,
        UpdateMemberRolesRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        initializeTenant(tenantId);
        TenantMemberEntity member = requireMemberInTenant(tenantId, memberId);
        List<Long> roleIds = request.roleIds() == null ? List.of() : request.roleIds().stream().distinct().toList();
        List<RoleEntity> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND, "角色不存在。");
        }
        for (RoleEntity role : roles) {
            if (!tenantId.equals(role.getTenantId()) || role.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.ROLE_NOT_FOUND, "角色不存在。");
            }
            if (!RoleStatus.ACTIVE.name().equals(role.getStatus())) {
                throw new BusinessException(ErrorCode.ROLE_DISABLED, "角色已停用，不能分配。");
            }
        }
        memberRoleMapper.deleteByMemberId(memberId);
        LocalDateTime now = LocalDateTime.now();
        for (RoleEntity role : roles) {
            MemberRoleEntity memberRole = new MemberRoleEntity();
            memberRole.setMemberId(memberId);
            memberRole.setRoleId(role.getId());
            memberRole.setCreatedBy(context.userId());
            memberRole.setCreatedAt(now);
            memberRoleMapper.insert(memberRole);
        }
        operationLogService.record(context.userId(), tenantId, "UPDATE_MEMBER_ROLES", memberId, OperationResult.SUCCESS, servletRequest);
        return memberRoles(member.getTenantId(), member.getId());
    }

    public List<PermissionTreeNodeResponse> permissionTree() {
        ensurePermissions();
        Map<String, List<PermissionEntity>> byResource = permissionMapper.selectList(null)
            .stream()
            .sorted(Comparator.comparing(PermissionEntity::getResource).thenComparing(PermissionEntity::getAction))
            .collect(Collectors.groupingBy(PermissionEntity::getResource, LinkedHashMap::new, Collectors.toList()));
        List<PermissionTreeNodeResponse> nodes = new ArrayList<>();
        for (Map.Entry<String, List<PermissionEntity>> entry : byResource.entrySet()) {
            List<PermissionTreeNodeResponse> children = entry.getValue()
                .stream()
                .map(permission -> new PermissionTreeNodeResponse(
                    permission.getCode(),
                    permission.getName(),
                    permission.getResource(),
                    permission.getCode(),
                    List.of()
                ))
                .toList();
            nodes.add(new PermissionTreeNodeResponse(entry.getKey(), resourceName(entry.getKey()), entry.getKey(), null, children));
        }
        return nodes;
    }

    @Transactional
    public AuthPermissionsResponse currentPermissions(HttpServletRequest request) {
        TenantContext context = resolveCurrentTenant(request);
        initializeTenant(context.tenantId());
        Set<String> permissions = rbacPermissionService.permissionCodes(context);
        List<String> menus = permissions.stream()
            .map(code -> code.split(":")[0])
            .distinct()
            .toList();
        return new AuthPermissionsResponse(menus, permissions);
    }

    @Transactional
    public void syncOwnerTransfer(Long tenantId, Long previousOwnerMemberId, Long nextOwnerMemberId) {
        initializeTenant(tenantId);
        RoleEntity ownerRole = roleMapper.selectActiveByTenantIdAndCode(tenantId, "OWNER");
        RoleEntity memberRole = roleMapper.selectActiveByTenantIdAndCode(tenantId, "MEMBER");
        replaceMemberRoles(previousOwnerMemberId, List.of(memberRole), null);
        replaceMemberRoles(nextOwnerMemberId, List.of(ownerRole), null);
    }

    @Transactional
    public void removeMemberRoles(Long memberId) {
        memberRoleMapper.deleteByMemberId(memberId);
    }

    private void initializeTenant(Long tenantId) {
        ensurePermissions();
        LocalDateTime now = LocalDateTime.now();
        RoleEntity ownerRole = ensureSystemRole(tenantId, "OWNER", "Owner", "团队所有者", true, now);
        ensureSystemRole(tenantId, "ADMIN", "Admin", "团队管理员", true, now);
        RoleEntity memberRole = ensureSystemRole(tenantId, "MEMBER", "Member", "普通成员", true, now);
        ensureRolePermissions(ownerRole, RbacPermissions.ALL.stream().map(RbacPermissionDefinition::code).toList());
        ensureRolePermissions(roleMapper.selectActiveByTenantIdAndCode(tenantId, "ADMIN"),
            RbacPermissions.ALL.stream().map(RbacPermissionDefinition::code).filter(code -> !"ROLE:DELETE".equals(code)).toList());

        for (TenantMemberEntity member : tenantMemberMapper.selectActiveByTenantId(tenantId)) {
            if (!memberRoleMapper.selectByMemberId(member.getId()).isEmpty()) {
                continue;
            }
            if (MemberType.OWNER.name().equals(member.getMemberType())) {
                insertMemberRoleIfAbsent(member.getId(), ownerRole, null);
            } else {
                insertMemberRoleIfAbsent(member.getId(), memberRole, null);
            }
        }
    }

    private void ensurePermissions() {
        LocalDateTime now = LocalDateTime.now();
        for (RbacPermissionDefinition definition : RbacPermissions.ALL) {
            PermissionEntity permission = permissionMapper.selectByCode(definition.code());
            if (permission == null) {
                permission = new PermissionEntity();
                permission.setCode(definition.code());
                permission.setName(definition.name());
                permission.setType(definition.type().name());
                permission.setResource(definition.resource());
                permission.setAction(definition.action());
                permission.setCreatedAt(now);
                permission.setUpdatedAt(now);
                try {
                    permissionMapper.insert(permission);
                } catch (DuplicateKeyException ignored) {
                    // Another request initialized the same permission first.
                }
            }
        }
    }

    private RoleEntity ensureSystemRole(
        Long tenantId,
        String code,
        String name,
        String description,
        boolean isDefault,
        LocalDateTime now
    ) {
        RoleEntity role = roleMapper.selectActiveByTenantIdAndCode(tenantId, code);
        if (role != null) {
            return role;
        }
        role = new RoleEntity();
        role.setTenantId(tenantId);
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        role.setRoleType(RoleType.SYSTEM.name());
        role.setStatus(RoleStatus.ACTIVE.name());
        role.setIsDefault(isDefault);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        try {
            roleMapper.insert(role);
            return role;
        } catch (DuplicateKeyException ignored) {
            return roleMapper.selectActiveByTenantIdAndCode(tenantId, code);
        }
    }

    private void ensureRolePermissions(RoleEntity role, List<String> permissionCodes) {
        Set<String> codes = permissionCodes == null ? Set.of() : permissionCodes.stream()
            .map(String::trim)
            .filter(code -> !code.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<PermissionEntity> permissions = permissionMapper.selectByCodes(codes);
        if (permissions.size() != codes.size()) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND, "权限不存在。");
        }
        LocalDateTime now = LocalDateTime.now();
        for (PermissionEntity permission : permissions) {
            if (rolePermissionMapper.selectByRoleIdAndPermissionId(role.getId(), permission.getId()) != null) {
                continue;
            }
            RolePermissionEntity rolePermission = new RolePermissionEntity();
            rolePermission.setRoleId(role.getId());
            rolePermission.setPermissionId(permission.getId());
            rolePermission.setCreatedAt(now);
            try {
                rolePermissionMapper.insert(rolePermission);
            } catch (DuplicateKeyException ignored) {
                // Another request created this role-permission edge first.
            }
        }
    }

    private void replaceRolePermissions(RoleEntity role, List<String> permissionCodes) {
        Set<String> codes = permissionCodes == null ? Set.of() : permissionCodes.stream()
            .map(String::trim)
            .filter(code -> !code.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<PermissionEntity> permissions = permissionMapper.selectByCodes(codes);
        if (permissions.size() != codes.size()) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND, "权限不存在。");
        }
        rolePermissionMapper.deleteByRoleId(role.getId());
        LocalDateTime now = LocalDateTime.now();
        for (PermissionEntity permission : permissions) {
            RolePermissionEntity rolePermission = new RolePermissionEntity();
            rolePermission.setRoleId(role.getId());
            rolePermission.setPermissionId(permission.getId());
            rolePermission.setCreatedAt(now);
            try {
                rolePermissionMapper.insert(rolePermission);
            } catch (DuplicateKeyException ignored) {
                // Keep replacement idempotent if the same save is submitted concurrently.
            }
        }
    }

    private void replaceMemberRoles(Long memberId, List<RoleEntity> roles, Long createdBy) {
        memberRoleMapper.deleteByMemberId(memberId);
        LocalDateTime now = LocalDateTime.now();
        for (RoleEntity role : roles) {
            MemberRoleEntity memberRole = new MemberRoleEntity();
            memberRole.setMemberId(memberId);
            memberRole.setRoleId(role.getId());
            memberRole.setCreatedBy(createdBy);
            memberRole.setCreatedAt(now);
            try {
                memberRoleMapper.insert(memberRole);
            } catch (DuplicateKeyException ignored) {
                // Keep replacement idempotent if the same assignment is submitted concurrently.
            }
        }
    }

    private void insertMemberRoleIfAbsent(Long memberId, RoleEntity role, Long createdBy) {
        if (memberRoleMapper.selectByMemberIdAndRoleId(memberId, role.getId()) != null) {
            return;
        }
        MemberRoleEntity memberRole = new MemberRoleEntity();
        memberRole.setMemberId(memberId);
        memberRole.setRoleId(role.getId());
        memberRole.setCreatedBy(createdBy);
        memberRole.setCreatedAt(LocalDateTime.now());
        try {
            memberRoleMapper.insert(memberRole);
        } catch (DuplicateKeyException ignored) {
            // Another request assigned the default role first.
        }
    }

    private RoleEntity requireRoleInTenant(Long tenantId, Long roleId) {
        RoleEntity role = roleMapper.selectById(roleId);
        if (role == null || !tenantId.equals(role.getTenantId()) || role.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND, "角色不存在。");
        }
        return role;
    }

    private TenantMemberEntity requireMemberInTenant(Long tenantId, Long memberId) {
        TenantMemberEntity member = tenantMemberMapper.selectById(memberId);
        if (member == null || !tenantId.equals(member.getTenantId()) || !MemberStatus.ACTIVE.name().equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "团队成员不存在。");
        }
        return member;
    }

    private List<RoleEntity> rolesForMember(Long memberId) {
        List<Long> roleIds = memberRoleMapper.selectByMemberId(memberId)
            .stream()
            .map(MemberRoleEntity::getRoleId)
            .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds)
            .stream()
            .filter(role -> role != null && role.getDeletedAt() == null)
            .toList();
    }

    private TenantContext resolveCurrentTenant(HttpServletRequest request) {
        String header = request.getHeader("X-Tenant-Id");
        if (header != null && !header.isBlank()) {
            try {
                return tenantContextResolver.requireActiveMember(Long.valueOf(header));
            } catch (NumberFormatException exception) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前创作团队标识不正确。");
            }
        }
        Long userId = CurrentUserHolder.require().userId();
        return currentTenantStore.get(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "尚未选择当前创作团队。"));
    }

    private void ensureOwnerMutable(RoleEntity role) {
        if ("OWNER".equals(role.getCode())) {
            throw new BusinessException(ErrorCode.OWNER_ROLE_IMMUTABLE, "Owner角色不可修改。");
        }
    }

    private String validateRoleCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (!code.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色编码需为2-64位大写字母、数字或下划线。");
        }
        return code;
    }

    private String validateRoleName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() < 2 || name.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色名称需为2-64个字符。");
        }
        return name;
    }

    private RoleStatus validateRoleStatus(String rawStatus) {
        try {
            return RoleStatus.valueOf(rawStatus);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色状态不正确。");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String resourceName(String resource) {
        return switch (resource) {
            case "TENANT" -> "团队管理";
            case "MEMBER" -> "成员管理";
            case "ROLE" -> "角色管理";
            case "PROJECT" -> "项目管理";
            case "SCRIPT" -> "剧本管理";
            case "AI_VOICE_TASK", "AI_VOICE_RESULT" -> "语音合成";
            case "SUBTITLE" -> "字幕管理";
            case "SHOT_COMPOSE" -> "单镜头合成";
            case "EPISODE_COMPOSE", "EPISODE_VERSION" -> "单集成片";
            default -> resource;
        };
    }
}
