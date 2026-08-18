package com.antshorttv.project;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.member.MemberStatus;
import com.antshorttv.member.TenantMemberEntity;
import com.antshorttv.member.TenantMemberMapper;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.rbac.PermissionEntity;
import com.antshorttv.rbac.PermissionMapper;
import com.antshorttv.rbac.PermissionType;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    private static final List<DefaultProjectRole> DEFAULT_ROLES = List.of(
        new DefaultProjectRole("PROJECT_OWNER", "项目负责人", ProjectDataScope.ALL, List.of(
            "PROJECT:VIEW",
            "PROJECT:EDIT",
            "PROJECT_MEMBER:VIEW",
            "PROJECT_MEMBER:ADD",
            "PROJECT_MEMBER:UPDATE",
            "PROJECT_MEMBER:REMOVE",
            "PROJECT_ROLE:VIEW",
            "PROJECT_ROLE:CREATE",
            "PROJECT_ROLE:UPDATE",
            "PROJECT_ROLE:DELETE",
            "PROJECT_ROLE:PERMISSION",
            "SCRIPT:VIEW",
            "SCRIPT:CREATE",
            "SCRIPT:EDIT",
            "SCRIPT:DELETE",
            "SCRIPT:AI_GENERATE",
            "SCRIPT:AI_REWRITE",
            "ELEMENT:VIEW",
            "ELEMENT:AI_EXTRACT",
            "ELEMENT:EDIT",
            "STORYBOARD:VIEW",
            "STORYBOARD:AI_BREAKDOWN",
            "STORYBOARD:EDIT",
            "PROMPT:AI_GENERATE",
            "AI_SERVICE:USE",
            "AI_VIDEO_TASK:VIEW",
            "AI_VIDEO_TASK:CREATE",
            "AI_VIDEO_TASK:CANCEL",
            "AI_VIDEO_TASK:DELETE",
            "AI_VIDEO_RESULT:SAVE",
            "AI_VIDEO_RESULT:BIND",
            "AI_VIDEO_RESULT:DOWNLOAD"
        )),
        new DefaultProjectRole("WRITER", "编剧", ProjectDataScope.PROJECT, List.of("PROJECT:VIEW")),
        new DefaultProjectRole("DIRECTOR", "导演", ProjectDataScope.PROJECT, List.of(
            "PROJECT:VIEW",
            "AI_VIDEO_TASK:VIEW",
            "AI_VIDEO_TASK:CREATE",
            "AI_VIDEO_TASK:CANCEL",
            "AI_VIDEO_RESULT:BIND"
        )),
        new DefaultProjectRole("PRODUCER", "制片", ProjectDataScope.PROJECT, List.of(
            "PROJECT:VIEW",
            "AI_VIDEO_TASK:VIEW",
            "AI_VIDEO_RESULT:SAVE",
            "AI_VIDEO_RESULT:DOWNLOAD"
        )),
        new DefaultProjectRole("MEMBER", "项目成员", ProjectDataScope.PROJECT, List.of("PROJECT:VIEW"))
    );

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectRoleMapper projectRoleMapper;
    private final ProjectRolePermissionMapper projectRolePermissionMapper;
    private final ProjectOperationLogMapper projectOperationLogMapper;
    private final OrganizationMapper organizationMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final UserMapper userMapper;
    private final PermissionMapper permissionMapper;
    private final RbacPermissionService rbacPermissionService;
    private final TenantContextResolver tenantContextResolver;
    private final OperationLogService operationLogService;

    public ProjectService(
        ProjectMapper projectMapper,
        ProjectMemberMapper projectMemberMapper,
        ProjectRoleMapper projectRoleMapper,
        ProjectRolePermissionMapper projectRolePermissionMapper,
        ProjectOperationLogMapper projectOperationLogMapper,
        OrganizationMapper organizationMapper,
        TenantMemberMapper tenantMemberMapper,
        UserMapper userMapper,
        PermissionMapper permissionMapper,
        RbacPermissionService rbacPermissionService,
        TenantContextResolver tenantContextResolver,
        OperationLogService operationLogService
    ) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectRoleMapper = projectRoleMapper;
        this.projectRolePermissionMapper = projectRolePermissionMapper;
        this.projectOperationLogMapper = projectOperationLogMapper;
        this.organizationMapper = organizationMapper;
        this.tenantMemberMapper = tenantMemberMapper;
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
        this.rbacPermissionService = rbacPermissionService;
        this.tenantContextResolver = tenantContextResolver;
        this.operationLogService = operationLogService;
    }

    public List<ProjectResponse> list(Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        return projectMapper.selectByTenantId(tenantId)
            .stream()
            .map(project -> toProjectResponse(project, tenantId))
            .toList();
    }

    @Transactional
    public ProjectResponse create(Long tenantId, CreateProjectRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        validateTenantMember(tenantId, request.ownerId());
        validateOrganization(tenantId, request.organizationId());
        String code = normalizeCode(request.code());
        if (projectMapper.selectByTenantIdAndCode(tenantId, code) != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目编码已存在。");
        }

        LocalDateTime now = LocalDateTime.now();
        ProjectEntity project = new ProjectEntity();
        project.tenantId = tenantId;
        project.organizationId = request.organizationId();
        project.name = validateName(request.name());
        project.code = code;
        project.description = request.description();
        project.coverUrl = request.coverUrl();
        project.ownerId = request.ownerId();
        project.status = ProjectStatus.NOT_STARTED.name();
        project.startDate = request.startDate();
        project.endDate = request.endDate();
        project.createdBy = context.userId();
        project.createdAt = now;
        project.updatedAt = now;
        projectMapper.insert(project);

        Map<String, ProjectRoleEntity> defaultRoles = createDefaultRoles(tenantId, project.id, context.userId(), now);
        addOwnerMember(tenantId, project.id, request.ownerId(), request.organizationId(), defaultRoles.get("PROJECT_OWNER").id, context.userId(), now);
        recordProjectLog(tenantId, project.id, context.userId(), "PROJECT_CREATE", "PROJECT", project.id, null, project.name, servletRequest);
        operationLogService.record(context.userId(), tenantId, "CREATE_PROJECT", project.id, OperationResult.SUCCESS, servletRequest);
        return toProjectResponse(project, tenantId);
    }

    public ProjectResponse detail(Long tenantId, Long id) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProject(tenantId, id);
        requireProjectAccess(context, project);
        return toProjectResponse(project, tenantId);
    }

    @Transactional
    public ProjectResponse update(Long tenantId, Long id, UpdateProjectRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProject(tenantId, id);
        validateWritable(project);
        validateOrganization(tenantId, request.organizationId());
        project.organizationId = request.organizationId();
        project.name = validateName(request.name());
        project.description = request.description();
        project.coverUrl = request.coverUrl();
        project.startDate = request.startDate();
        project.endDate = request.endDate();
        project.updatedAt = LocalDateTime.now();
        projectMapper.updateById(project);
        recordProjectLog(tenantId, id, context.userId(), "PROJECT_UPDATE", "PROJECT", id, null, project.name, servletRequest);
        return toProjectResponse(project, tenantId);
    }

    @Transactional
    public void delete(Long tenantId, Long id, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProject(tenantId, id);
        project.deletedAt = LocalDateTime.now();
        project.updatedAt = project.deletedAt;
        projectMapper.updateById(project);
        recordProjectLog(tenantId, id, context.userId(), "PROJECT_DELETE", "PROJECT", id, null, project.name, servletRequest);
    }

    @Transactional
    public ProjectResponse updateStatus(Long tenantId, Long id, UpdateProjectStatusRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProject(tenantId, id);
        ProjectStatus nextStatus = validateProjectStatus(request.status());
        if (ProjectStatus.ARCHIVED.name().equals(project.status)
            && nextStatus != ProjectStatus.ARCHIVED
            && !rbacPermissionService.hasPermission(context, "PROJECT:EDIT")) {
            throw new BusinessException(ErrorCode.PROJECT_ARCHIVED, "归档项目只能由租户级项目管理员恢复。");
        }
        project.status = nextStatus.name();
        project.updatedAt = LocalDateTime.now();
        projectMapper.updateById(project);
        recordProjectLog(tenantId, id, context.userId(), "PROJECT_STATUS_UPDATE", "PROJECT", id, null, project.status, servletRequest);
        return toProjectResponse(project, tenantId);
    }

    @Transactional
    public ProjectResponse updateOwner(Long tenantId, Long id, UpdateProjectOwnerRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProject(tenantId, id);
        validateWritable(project);
        validateTenantMember(tenantId, request.ownerId());
        ProjectRoleEntity ownerRole = requireRoleByCode(tenantId, id, "PROJECT_OWNER");
        ProjectRoleEntity memberRole = requireRoleByCode(tenantId, id, "MEMBER");
        Long previousOwnerId = project.ownerId;
        ProjectMemberEntity member = projectMemberMapper.selectByProjectIdAndUserId(tenantId, id, request.ownerId());
        if (member == null) {
            addOwnerMember(tenantId, id, request.ownerId(), project.organizationId, ownerRole.id, context.userId(), LocalDateTime.now());
        } else {
            member.roleId = ownerRole.id;
            member.status = ProjectMemberStatus.ACTIVE.name();
            member.updatedAt = LocalDateTime.now();
            projectMemberMapper.updateById(member);
        }
        if (!request.ownerId().equals(previousOwnerId)) {
            ProjectMemberEntity previousOwner = projectMemberMapper.selectActiveByProjectIdAndUserId(tenantId, id, previousOwnerId);
            if (previousOwner != null && ownerRole.id.equals(previousOwner.roleId)) {
                previousOwner.roleId = memberRole.id;
                previousOwner.updatedAt = LocalDateTime.now();
                projectMemberMapper.updateById(previousOwner);
            }
        }
        project.ownerId = request.ownerId();
        project.updatedAt = LocalDateTime.now();
        projectMapper.updateById(project);
        recordProjectLog(tenantId, id, context.userId(), "PROJECT_OWNER_UPDATE", "PROJECT", id, null, String.valueOf(request.ownerId()), servletRequest);
        return toProjectResponse(project, tenantId);
    }

    public List<ProjectMemberResponse> members(Long tenantId, Long projectId) {
        tenantContextResolver.requireActiveMember(tenantId);
        requireProject(tenantId, projectId);
        return projectMemberMapper.selectActiveByProjectId(tenantId, projectId)
            .stream()
            .map(member -> toMemberResponse(member, tenantId))
            .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(Long tenantId, Long projectId, AddProjectMemberRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProject(tenantId, projectId);
        validateWritable(project);
        validateTenantMember(tenantId, request.userId());
        validateOrganization(tenantId, request.organizationId());
        ProjectRoleEntity role = request.roleId() == null
            ? requireRoleByCode(tenantId, projectId, "MEMBER")
            : requireRole(tenantId, projectId, request.roleId());
        ProjectMemberEntity existing = projectMemberMapper.selectByProjectIdAndUserId(tenantId, projectId, request.userId());
        if (existing != null && ProjectMemberStatus.ACTIVE.name().equals(existing.status)) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_EXISTS, "项目成员已存在。");
        }
        LocalDateTime now = LocalDateTime.now();
        ProjectMemberEntity member = existing == null ? new ProjectMemberEntity() : existing;
        member.tenantId = tenantId;
        member.projectId = projectId;
        member.userId = request.userId();
        member.organizationId = request.organizationId();
        member.roleId = role.id;
        member.status = ProjectMemberStatus.ACTIVE.name();
        member.createdBy = context.userId();
        member.updatedAt = now;
        if (existing == null) {
            member.joinedAt = now;
            member.createdAt = now;
            projectMemberMapper.insert(member);
        } else {
            projectMemberMapper.updateById(member);
        }
        recordProjectLog(tenantId, projectId, context.userId(), "PROJECT_MEMBER_ADD", "PROJECT_MEMBER", member.id, null, String.valueOf(request.userId()), servletRequest);
        return toMemberResponse(member, tenantId);
    }

    @Transactional
    public void removeMember(Long tenantId, Long projectId, Long userId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProject(tenantId, projectId);
        validateWritable(project);
        ProjectMemberEntity member = requireProjectMember(tenantId, projectId, userId);
        member.status = ProjectMemberStatus.REMOVED.name();
        member.updatedAt = LocalDateTime.now();
        projectMemberMapper.updateById(member);
        recordProjectLog(tenantId, projectId, context.userId(), "PROJECT_MEMBER_REMOVE", "PROJECT_MEMBER", member.id, String.valueOf(userId), null, servletRequest);
    }

    @Transactional
    public ProjectMemberResponse updateMemberRole(Long tenantId, Long projectId, Long userId, UpdateProjectMemberRoleRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProject(tenantId, projectId);
        validateWritable(project);
        ProjectRoleEntity role = requireRole(tenantId, projectId, request.roleId());
        ProjectMemberEntity member = requireProjectMember(tenantId, projectId, userId);
        member.roleId = role.id;
        member.updatedAt = LocalDateTime.now();
        projectMemberMapper.updateById(member);
        recordProjectLog(tenantId, projectId, context.userId(), "PROJECT_MEMBER_ROLE_UPDATE", "PROJECT_MEMBER", member.id, null, String.valueOf(role.id), servletRequest);
        return toMemberResponse(member, tenantId);
    }

    public List<ProjectRoleResponse> roles(Long tenantId, Long projectId) {
        tenantContextResolver.requireActiveMember(tenantId);
        requireProject(tenantId, projectId);
        return projectRoleMapper.selectByProjectId(tenantId, projectId)
            .stream()
            .map(this::toRoleResponse)
            .toList();
    }

    @Transactional
    public ProjectRoleResponse createRole(Long tenantId, Long projectId, CreateProjectRoleRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProject(tenantId, projectId);
        validateWritable(project);
        String code = normalizeCode(request.code());
        if (projectRoleMapper.selectByProjectIdAndCode(tenantId, projectId, code) != null) {
            throw new BusinessException(ErrorCode.ROLE_NAME_DUPLICATE, "项目角色编码已存在。");
        }
        ProjectRoleEntity role = new ProjectRoleEntity();
        role.tenantId = tenantId;
        role.projectId = projectId;
        role.code = code;
        role.name = validateRoleName(request.name());
        role.description = request.description();
        role.isSystem = false;
        role.status = ProjectRoleStatus.ACTIVE.name();
        role.dataScope = normalizeDataScope(request.dataScope()).name();
        role.createdBy = context.userId();
        role.createdAt = LocalDateTime.now();
        role.updatedAt = role.createdAt;
        projectRoleMapper.insert(role);
        replaceRolePermissions(tenantId, projectId, role.id, request.permissionCodes());
        recordProjectLog(tenantId, projectId, context.userId(), "PROJECT_ROLE_CREATE", "PROJECT_ROLE", role.id, null, role.name, servletRequest);
        return toRoleResponse(role);
    }

    @Transactional
    public ProjectRoleResponse updateRole(Long tenantId, Long projectId, Long roleId, UpdateProjectRoleRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProject(tenantId, projectId);
        validateWritable(project);
        ProjectRoleEntity role = requireRole(tenantId, projectId, roleId);
        role.name = validateRoleName(request.name());
        role.description = request.description();
        role.status = request.status() == null ? role.status : validateProjectRoleStatus(request.status()).name();
        role.dataScope = normalizeDataScope(request.dataScope()).name();
        role.updatedAt = LocalDateTime.now();
        projectRoleMapper.updateById(role);
        if (request.permissionCodes() != null) {
            replaceRolePermissions(tenantId, projectId, roleId, request.permissionCodes());
        }
        recordProjectLog(tenantId, projectId, context.userId(), "PROJECT_ROLE_UPDATE", "PROJECT_ROLE", role.id, null, role.name, servletRequest);
        return toRoleResponse(role);
    }

    @Transactional
    public void deleteRole(Long tenantId, Long projectId, Long roleId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProject(tenantId, projectId);
        validateWritable(project);
        ProjectRoleEntity role = requireRole(tenantId, projectId, roleId);
        if (Boolean.TRUE.equals(role.isSystem)) {
            throw new BusinessException(ErrorCode.OWNER_ROLE_DELETE_BLOCKED, "系统项目角色不能删除。");
        }
        long memberCount = projectMemberMapper.countActiveByRoleId(tenantId, projectId, roleId);
        if (memberCount > 0) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE, "当前项目角色已分配给 %d 名成员，请先完成角色调整。".formatted(memberCount));
        }
        projectRolePermissionMapper.deleteByRoleId(tenantId, projectId, roleId);
        projectRoleMapper.deleteById(roleId);
        recordProjectLog(tenantId, projectId, context.userId(), "PROJECT_ROLE_DELETE", "PROJECT_ROLE", role.id, role.name, null, servletRequest);
    }

    public List<ProjectRolePermissionResponse> rolePermissions(Long tenantId, Long projectId, Long roleId) {
        tenantContextResolver.requireActiveMember(tenantId);
        requireProject(tenantId, projectId);
        requireRole(tenantId, projectId, roleId);
        List<Long> permissionIds = projectRolePermissionMapper.selectByRoleIds(tenantId, projectId, List.of(roleId))
            .stream()
            .map(permission -> permission.permissionId)
            .distinct()
            .toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectBatchIds(permissionIds)
            .stream()
            .map(permission -> new ProjectRolePermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getResource(),
                permission.getAction()
            ))
            .toList();
    }

    @Transactional
    public List<ProjectRolePermissionResponse> updateRolePermissions(
        Long tenantId,
        Long projectId,
        Long roleId,
        UpdateProjectRolePermissionsRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProject(tenantId, projectId);
        validateWritable(project);
        requireRole(tenantId, projectId, roleId);
        replaceRolePermissions(tenantId, projectId, roleId, request.permissionCodes());
        recordProjectLog(tenantId, projectId, context.userId(), "PROJECT_ROLE_PERMISSION_UPDATE", "PROJECT_ROLE", roleId, null, String.valueOf(request.permissionCodes()), servletRequest);
        return rolePermissions(tenantId, projectId, roleId);
    }

    private Map<String, ProjectRoleEntity> createDefaultRoles(Long tenantId, Long projectId, Long createdBy, LocalDateTime now) {
        ensureProjectPermissions(now);
        Map<String, PermissionEntity> permissionsByCode = permissionMapper.selectByCodes(
            DEFAULT_ROLES.stream()
                .flatMap(role -> role.permissionCodes().stream())
                .distinct()
                .toList()
        ).stream().collect(Collectors.toMap(PermissionEntity::getCode, Function.identity()));
        Map<String, ProjectRoleEntity> roles = new LinkedHashMap<>();
        for (DefaultProjectRole defaultRole : DEFAULT_ROLES) {
            ProjectRoleEntity role = new ProjectRoleEntity();
            role.tenantId = tenantId;
            role.projectId = projectId;
            role.name = defaultRole.name();
            role.code = defaultRole.code();
            role.description = null;
            role.isSystem = true;
            role.status = ProjectRoleStatus.ACTIVE.name();
            role.dataScope = defaultRole.dataScope().name();
            role.createdBy = createdBy;
            role.createdAt = now;
            role.updatedAt = now;
            projectRoleMapper.insert(role);
            roles.put(role.code, role);
            for (String permissionCode : defaultRole.permissionCodes()) {
                PermissionEntity permission = permissionsByCode.get(permissionCode);
                if (permission != null) {
                    insertRolePermission(tenantId, projectId, role.id, permission.getId(), now);
                }
            }
        }
        return roles;
    }

    private void ensureProjectPermissions(LocalDateTime now) {
        List<ProjectPermissionSeed> seeds = List.of(
            new ProjectPermissionSeed("PROJECT:VIEW", "查看项目", "PROJECT", "VIEW"),
            new ProjectPermissionSeed("PROJECT:EDIT", "编辑项目", "PROJECT", "EDIT"),
            new ProjectPermissionSeed("PROJECT_MEMBER:VIEW", "查看项目成员", "PROJECT_MEMBER", "VIEW"),
            new ProjectPermissionSeed("PROJECT_MEMBER:ADD", "添加项目成员", "PROJECT_MEMBER", "ADD"),
            new ProjectPermissionSeed("PROJECT_MEMBER:UPDATE", "修改项目成员", "PROJECT_MEMBER", "UPDATE"),
            new ProjectPermissionSeed("PROJECT_MEMBER:REMOVE", "移除项目成员", "PROJECT_MEMBER", "REMOVE"),
            new ProjectPermissionSeed("PROJECT_ROLE:VIEW", "查看项目角色", "PROJECT_ROLE", "VIEW"),
            new ProjectPermissionSeed("PROJECT_ROLE:CREATE", "创建项目角色", "PROJECT_ROLE", "CREATE"),
            new ProjectPermissionSeed("PROJECT_ROLE:UPDATE", "编辑项目角色", "PROJECT_ROLE", "UPDATE"),
            new ProjectPermissionSeed("PROJECT_ROLE:DELETE", "删除项目角色", "PROJECT_ROLE", "DELETE"),
            new ProjectPermissionSeed("PROJECT_ROLE:PERMISSION", "配置项目角色权限", "PROJECT_ROLE", "PERMISSION"),
            new ProjectPermissionSeed("SCRIPT:VIEW", "查看剧本", "SCRIPT", "VIEW"),
            new ProjectPermissionSeed("SCRIPT:CREATE", "创建剧本", "SCRIPT", "CREATE"),
            new ProjectPermissionSeed("SCRIPT:EDIT", "编辑剧本", "SCRIPT", "EDIT"),
            new ProjectPermissionSeed("SCRIPT:DELETE", "删除剧本", "SCRIPT", "DELETE"),
            new ProjectPermissionSeed("SCRIPT:AI_GENERATE", "AI生成剧本", "SCRIPT", "AI_GENERATE"),
            new ProjectPermissionSeed("SCRIPT:AI_REWRITE", "AI改写剧本", "SCRIPT", "AI_REWRITE"),
            new ProjectPermissionSeed("ELEMENT:VIEW", "查看元素库", "ELEMENT", "VIEW"),
            new ProjectPermissionSeed("ELEMENT:AI_EXTRACT", "AI提取元素", "ELEMENT", "AI_EXTRACT"),
            new ProjectPermissionSeed("ELEMENT:EDIT", "编辑元素", "ELEMENT", "EDIT"),
            new ProjectPermissionSeed("STORYBOARD:VIEW", "查看分镜", "STORYBOARD", "VIEW"),
            new ProjectPermissionSeed("STORYBOARD:AI_BREAKDOWN", "AI拆解分镜", "STORYBOARD", "AI_BREAKDOWN"),
            new ProjectPermissionSeed("STORYBOARD:EDIT", "编辑分镜", "STORYBOARD", "EDIT"),
            new ProjectPermissionSeed("PROMPT:AI_GENERATE", "AI生成提示词", "PROMPT", "AI_GENERATE"),
            new ProjectPermissionSeed("AI_SERVICE:USE", "使用AI服务", "AI_SERVICE", "USE"),
            new ProjectPermissionSeed("AI_VIDEO_TASK:VIEW", "查看视频生成任务", "AI_VIDEO_TASK", "VIEW"),
            new ProjectPermissionSeed("AI_VIDEO_TASK:CREATE", "创建视频生成任务", "AI_VIDEO_TASK", "CREATE"),
            new ProjectPermissionSeed("AI_VIDEO_TASK:CANCEL", "取消视频生成任务", "AI_VIDEO_TASK", "CANCEL"),
            new ProjectPermissionSeed("AI_VIDEO_TASK:DELETE", "删除视频生成记录", "AI_VIDEO_TASK", "DELETE"),
            new ProjectPermissionSeed("AI_VIDEO_RESULT:SAVE", "保存生成视频", "AI_VIDEO_RESULT", "SAVE"),
            new ProjectPermissionSeed("AI_VIDEO_RESULT:BIND", "设置分镜视频", "AI_VIDEO_RESULT", "BIND"),
            new ProjectPermissionSeed("AI_VIDEO_RESULT:DOWNLOAD", "下载生成视频", "AI_VIDEO_RESULT", "DOWNLOAD")
        );
        for (ProjectPermissionSeed seed : seeds) {
            if (permissionMapper.selectByCode(seed.code()) != null) {
                continue;
            }
            PermissionEntity permission = new PermissionEntity();
            permission.setCode(seed.code());
            permission.setName(seed.name());
            permission.setType(PermissionType.BUTTON.name());
            permission.setResource(seed.resource());
            permission.setAction(seed.action());
            permission.setCreatedAt(now);
            permission.setUpdatedAt(now);
            try {
                permissionMapper.insert(permission);
            } catch (DuplicateKeyException ignored) {
            }
        }
    }

    private void replaceRolePermissions(Long tenantId, Long projectId, Long roleId, List<String> permissionCodes) {
        projectRolePermissionMapper.deleteByRoleId(tenantId, projectId, roleId);
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }
        List<PermissionEntity> permissions = permissionMapper.selectByCodes(permissionCodes);
        if (permissions.size() != permissionCodes.stream().distinct().count()) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND, "权限资源不存在。");
        }
        LocalDateTime now = LocalDateTime.now();
        for (PermissionEntity permission : permissions) {
            insertRolePermission(tenantId, projectId, roleId, permission.getId(), now);
        }
    }

    private void insertRolePermission(Long tenantId, Long projectId, Long roleId, Long permissionId, LocalDateTime now) {
        ProjectRolePermissionEntity entity = new ProjectRolePermissionEntity();
        entity.tenantId = tenantId;
        entity.projectId = projectId;
        entity.roleId = roleId;
        entity.permissionId = permissionId;
        entity.createdAt = now;
        try {
            projectRolePermissionMapper.insert(entity);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private void addOwnerMember(Long tenantId, Long projectId, Long userId, Long organizationId, Long roleId, Long createdBy, LocalDateTime now) {
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.tenantId = tenantId;
        member.projectId = projectId;
        member.userId = userId;
        member.organizationId = organizationId;
        member.roleId = roleId;
        member.joinedAt = now;
        member.status = ProjectMemberStatus.ACTIVE.name();
        member.createdBy = createdBy;
        member.createdAt = now;
        member.updatedAt = now;
        projectMemberMapper.insert(member);
    }

    private ProjectEntity requireProject(Long tenantId, Long id) {
        ProjectEntity project = projectMapper.selectByTenantIdAndId(tenantId, id);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在。");
        }
        return project;
    }

    private ProjectRoleEntity requireRole(Long tenantId, Long projectId, Long roleId) {
        ProjectRoleEntity role = projectRoleMapper.selectByTenantProjectAndId(tenantId, projectId, roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND, "项目角色不存在。");
        }
        return role;
    }

    private ProjectRoleEntity requireRoleByCode(Long tenantId, Long projectId, String code) {
        ProjectRoleEntity role = projectRoleMapper.selectByProjectIdAndCode(tenantId, projectId, code);
        if (role == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND, "项目角色不存在。");
        }
        return role;
    }

    private ProjectMemberEntity requireProjectMember(Long tenantId, Long projectId, Long userId) {
        ProjectMemberEntity member = projectMemberMapper.selectActiveByProjectIdAndUserId(tenantId, projectId, userId);
        if (member == null) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND, "项目成员不存在。");
        }
        return member;
    }

    private void requireProjectAccess(TenantContext context, ProjectEntity project) {
        if (rbacPermissionService.hasPermission(context, "PROJECT:VIEW")) {
            return;
        }
        ProjectMemberEntity member = projectMemberMapper.selectActiveByProjectIdAndUserId(context.tenantId(), project.id, context.userId());
        if (member == null) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该项目。");
        }
    }

    private void validateWritable(ProjectEntity project) {
        if (ProjectStatus.ARCHIVED.name().equals(project.status)) {
            throw new BusinessException(ErrorCode.PROJECT_ARCHIVED, "归档项目不允许执行该操作。");
        }
    }

    private void validateTenantMember(Long tenantId, Long userId) {
        TenantMemberEntity member = tenantMemberMapper.selectByTenantIdAndUserId(tenantId, userId);
        if (member == null || !MemberStatus.ACTIVE.name().equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "用户必须是当前团队成员。");
        }
    }

    private void validateOrganization(Long tenantId, Long organizationId) {
        if (organizationId == null) {
            return;
        }
        if (organizationMapper.selectByTenantIdAndId(tenantId, organizationId) == null) {
            throw new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND, "组织不存在。");
        }
    }

    private ProjectResponse toProjectResponse(ProjectEntity project, Long tenantId) {
        OrganizationEntity organization = project.organizationId == null ? null : organizationMapper.selectByTenantIdAndId(tenantId, project.organizationId);
        UserEntity owner = userMapper.selectById(project.ownerId);
        return new ProjectResponse(
            project.id,
            project.tenantId,
            project.organizationId,
            organization == null ? null : organization.name,
            project.name,
            project.code,
            project.description,
            project.coverUrl,
            project.ownerId,
            owner == null ? null : owner.getNickname(),
            project.status,
            project.startDate,
            project.endDate,
            projectMemberMapper.countActiveByProjectId(tenantId, project.id),
            project.createdAt,
            project.updatedAt
        );
    }

    private ProjectMemberResponse toMemberResponse(ProjectMemberEntity member, Long tenantId) {
        UserEntity user = userMapper.selectById(member.userId);
        OrganizationEntity organization = member.organizationId == null ? null : organizationMapper.selectByTenantIdAndId(tenantId, member.organizationId);
        ProjectRoleEntity role = member.roleId == null ? null : projectRoleMapper.selectByTenantProjectAndId(tenantId, member.projectId, member.roleId);
        return new ProjectMemberResponse(
            member.id,
            member.tenantId,
            member.projectId,
            member.userId,
            user == null ? null : user.getNickname(),
            user == null ? null : user.getMobile(),
            member.organizationId,
            organization == null ? null : organization.name,
            member.roleId,
            role == null ? null : role.name,
            role == null ? null : role.code,
            member.status,
            member.joinedAt
        );
    }

    private ProjectRoleResponse toRoleResponse(ProjectRoleEntity role) {
        return new ProjectRoleResponse(
            role.id,
            role.tenantId,
            role.projectId,
            role.name,
            role.code,
            role.description,
            role.isSystem,
            role.status,
            role.dataScope,
            role.createdAt,
            role.updatedAt
        );
    }

    private void recordProjectLog(
        Long tenantId,
        Long projectId,
        Long userId,
        String operationType,
        String resourceType,
        Long resourceId,
        String beforeData,
        String afterData,
        HttpServletRequest request
    ) {
        ProjectOperationLogEntity log = new ProjectOperationLogEntity();
        log.tenantId = tenantId;
        log.projectId = projectId;
        log.userId = userId;
        log.operationType = operationType;
        log.resourceType = resourceType;
        log.resourceId = resourceId;
        log.beforeData = beforeData;
        log.afterData = afterData;
        log.ip = resolveIp(request);
        log.userAgent = request == null ? null : request.getHeader("User-Agent");
        log.createdAt = LocalDateTime.now();
        projectOperationLogMapper.insert(log);
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String validateName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() < 2 || name.length() > 200) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目名称需为2-200个字符。");
        }
        return name;
    }

    private String validateRoleName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目角色名称需为2-100个字符。");
        }
        return name;
    }

    private String normalizeCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (!code.matches("[A-Z][A-Z0-9_]{1,49}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "编码需为2-50位字母、数字或下划线。");
        }
        return code;
    }

    private ProjectDataScope normalizeDataScope(String rawDataScope) {
        if (rawDataScope == null || rawDataScope.isBlank()) {
            return ProjectDataScope.PROJECT;
        }
        try {
            return ProjectDataScope.valueOf(rawDataScope);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目数据范围不正确。");
        }
    }

    private ProjectStatus validateProjectStatus(String rawStatus) {
        try {
            return ProjectStatus.valueOf(rawStatus);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目状态不正确。");
        }
    }

    private ProjectRoleStatus validateProjectRoleStatus(String rawStatus) {
        try {
            return ProjectRoleStatus.valueOf(rawStatus);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目角色状态不正确。");
        }
    }

    private record DefaultProjectRole(
        String code,
        String name,
        ProjectDataScope dataScope,
        List<String> permissionCodes
    ) {
    }

    private record ProjectPermissionSeed(
        String code,
        String name,
        String resource,
        String action
    ) {
    }
}
