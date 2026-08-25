package com.antshorttv.rbac;

import com.antshorttv.common.ApiResponse;
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
@RequestMapping("/api")
public class RbacController {

    private final RbacService rbacService;

    public RbacController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @GetMapping("/tenants/{tenantId}/roles")
    @RequirePermission("ROLE:VIEW")
    public ApiResponse<List<RoleResponse>> listRoles(@PathVariable Long tenantId) {
        return ApiResponse.success(rbacService.listRoles(tenantId));
    }

    @PostMapping("/tenants/{tenantId}/roles")
    @RequirePermission("ROLE:CREATE")
    public ApiResponse<RoleResponse> createRole(
        @PathVariable Long tenantId,
        @Valid @RequestBody CreateRoleRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(rbacService.createRole(tenantId, request, servletRequest));
    }

    @GetMapping("/tenants/{tenantId}/roles/{roleId}")
    @RequirePermission("ROLE:VIEW")
    public ApiResponse<RoleResponse> detail(@PathVariable Long tenantId, @PathVariable Long roleId) {
        return ApiResponse.success(rbacService.detail(tenantId, roleId));
    }

    @PutMapping("/tenants/{tenantId}/roles/{roleId}")
    @RequirePermission("ROLE:EDIT")
    public ApiResponse<RoleResponse> updateRole(
        @PathVariable Long tenantId,
        @PathVariable Long roleId,
        @Valid @RequestBody UpdateRoleRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(rbacService.updateRole(tenantId, roleId, request, servletRequest));
    }

    @DeleteMapping("/tenants/{tenantId}/roles/{roleId}")
    @RequirePermission("ROLE:DELETE")
    public ApiResponse<Void> deleteRole(
        @PathVariable Long tenantId,
        @PathVariable Long roleId,
        HttpServletRequest servletRequest
    ) {
        rbacService.deleteRole(tenantId, roleId, servletRequest);
        return ApiResponse.ok();
    }

    @PutMapping("/tenants/{tenantId}/roles/{roleId}/status")
    @RequirePermission("ROLE:EDIT")
    public ApiResponse<RoleResponse> updateStatus(
        @PathVariable Long tenantId,
        @PathVariable Long roleId,
        @Valid @RequestBody UpdateRoleStatusRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(rbacService.updateStatus(tenantId, roleId, request, servletRequest));
    }

    @GetMapping("/tenants/{tenantId}/roles/{roleId}/permissions")
    @RequirePermission("ROLE:VIEW")
    public ApiResponse<List<PermissionResponse>> rolePermissions(@PathVariable Long tenantId, @PathVariable Long roleId) {
        return ApiResponse.success(rbacService.rolePermissions(tenantId, roleId));
    }

    @PutMapping("/tenants/{tenantId}/roles/{roleId}/permissions")
    @RequirePermission("ROLE:EDIT")
    public ApiResponse<List<PermissionResponse>> updateRolePermissions(
        @PathVariable Long tenantId,
        @PathVariable Long roleId,
        @RequestBody UpdateRolePermissionsRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(rbacService.updateRolePermissions(tenantId, roleId, request, servletRequest));
    }

    @GetMapping("/tenants/{tenantId}/members/{memberId}/roles")
    @RequirePermission("MEMBER:VIEW")
    public ApiResponse<List<RoleResponse>> memberRoles(@PathVariable Long tenantId, @PathVariable Long memberId) {
        return ApiResponse.success(rbacService.memberRoles(tenantId, memberId));
    }

    @PutMapping("/tenants/{tenantId}/members/{memberId}/roles")
    @RequirePermission("MEMBER:INVITE")
    public ApiResponse<List<RoleResponse>> updateMemberRoles(
        @PathVariable Long tenantId,
        @PathVariable Long memberId,
        @RequestBody UpdateMemberRolesRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(rbacService.updateMemberRoles(tenantId, memberId, request, servletRequest));
    }

    @GetMapping("/permissions/tree")
    public ApiResponse<List<PermissionTreeNodeResponse>> permissionTree() {
        return ApiResponse.success(rbacService.permissionTree());
    }

}
