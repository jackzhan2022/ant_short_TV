package com.antshorttv.project;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.rbac.RequirePermission;
import com.antshorttv.rbac.RequireProjectPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(HttpServletRequest request) {
        return ApiResponse.success(projectService.list(tenantId(request)));
    }

    @PostMapping
    @RequirePermission("PROJECT:CREATE")
    public ApiResponse<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest body, HttpServletRequest request) {
        return ApiResponse.success(projectService.create(tenantId(request), body, request));
    }

    @GetMapping("/{id}")
    @RequireProjectPermission(value = "PROJECT:VIEW", projectIdParameter = "id")
    public ApiResponse<ProjectResponse> detail(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(projectService.detail(tenantId(request), id));
    }

    @PutMapping("/{id}")
    @RequireProjectPermission(value = "PROJECT:EDIT", projectIdParameter = "id")
    public ApiResponse<ProjectResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateProjectRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(projectService.update(tenantId(request), id, body, request));
    }

    @DeleteMapping("/{id}")
    @RequireProjectPermission(value = "PROJECT:DELETE", projectIdParameter = "id")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        projectService.delete(tenantId(request), id, request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    @RequireProjectPermission(value = "PROJECT:EDIT", projectIdParameter = "id")
    public ApiResponse<ProjectResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateProjectStatusRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(projectService.updateStatus(tenantId(request), id, body, request));
    }

    @PutMapping("/{id}/owner")
    @RequireProjectPermission(value = "PROJECT:EDIT", projectIdParameter = "id")
    public ApiResponse<ProjectResponse> updateOwner(
        @PathVariable Long id,
        @Valid @RequestBody UpdateProjectOwnerRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(projectService.updateOwner(tenantId(request), id, body, request));
    }

    @GetMapping("/{id}/members")
    @RequireProjectPermission(value = "PROJECT_MEMBER:VIEW", projectIdParameter = "id")
    public ApiResponse<List<ProjectMemberResponse>> members(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(projectService.members(tenantId(request), id));
    }

    @PostMapping("/{id}/members")
    @RequireProjectPermission(value = "PROJECT_MEMBER:ADD", projectIdParameter = "id")
    public ApiResponse<ProjectMemberResponse> addMember(
        @PathVariable Long id,
        @Valid @RequestBody AddProjectMemberRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(projectService.addMember(tenantId(request), id, body, request));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @RequireProjectPermission(value = "PROJECT_MEMBER:REMOVE", projectIdParameter = "id")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable Long userId, HttpServletRequest request) {
        projectService.removeMember(tenantId(request), id, userId, request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/members/{userId}/role")
    @RequireProjectPermission(value = "PROJECT_MEMBER:UPDATE", projectIdParameter = "id")
    public ApiResponse<ProjectMemberResponse> updateMemberRole(
        @PathVariable Long id,
        @PathVariable Long userId,
        @Valid @RequestBody UpdateProjectMemberRoleRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(projectService.updateMemberRole(tenantId(request), id, userId, body, request));
    }

    @GetMapping("/{id}/roles")
    @RequireProjectPermission(value = "PROJECT_ROLE:VIEW", projectIdParameter = "id")
    public ApiResponse<List<ProjectRoleResponse>> roles(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(projectService.roles(tenantId(request), id));
    }

    @PostMapping("/{id}/roles")
    @RequireProjectPermission(value = "PROJECT_ROLE:CREATE", projectIdParameter = "id")
    public ApiResponse<ProjectRoleResponse> createRole(
        @PathVariable Long id,
        @Valid @RequestBody CreateProjectRoleRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(projectService.createRole(tenantId(request), id, body, request));
    }

    @PutMapping("/{id}/roles/{roleId}")
    @RequireProjectPermission(value = "PROJECT_ROLE:UPDATE", projectIdParameter = "id")
    public ApiResponse<ProjectRoleResponse> updateRole(
        @PathVariable Long id,
        @PathVariable Long roleId,
        @Valid @RequestBody UpdateProjectRoleRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(projectService.updateRole(tenantId(request), id, roleId, body, request));
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @RequireProjectPermission(value = "PROJECT_ROLE:DELETE", projectIdParameter = "id")
    public ApiResponse<Void> deleteRole(@PathVariable Long id, @PathVariable Long roleId, HttpServletRequest request) {
        projectService.deleteRole(tenantId(request), id, roleId, request);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/roles/{roleId}/permissions")
    @RequireProjectPermission(value = "PROJECT_ROLE:VIEW", projectIdParameter = "id")
    public ApiResponse<List<ProjectRolePermissionResponse>> rolePermissions(
        @PathVariable Long id,
        @PathVariable Long roleId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(projectService.rolePermissions(tenantId(request), id, roleId));
    }

    @PutMapping("/{id}/roles/{roleId}/permissions")
    @RequireProjectPermission(value = "PROJECT_ROLE:PERMISSION", projectIdParameter = "id")
    public ApiResponse<List<ProjectRolePermissionResponse>> updateRolePermissions(
        @PathVariable Long id,
        @PathVariable Long roleId,
        @RequestBody UpdateProjectRolePermissionsRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(projectService.updateRolePermissions(tenantId(request), id, roleId, body, request));
    }

    private Long tenantId(HttpServletRequest request) {
        return TenantRequestSupport.tenantId(request);
    }
}
