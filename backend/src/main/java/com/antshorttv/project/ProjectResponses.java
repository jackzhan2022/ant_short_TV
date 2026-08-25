package com.antshorttv.project;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

record ProjectResponse(
    Long id,
    Long tenantId,
    String name,
    String code,
    String description,
    String coverUrl,
    String coverSource,
    Long ownerId,
    String ownerName,
    String status,
    LocalDate startDate,
    LocalDate endDate,
    String aspectRatio,
    String fileFormat,
    String scriptType,
    String breakdownStrength,
    String visualStyle,
    String initialScriptContent,
    Long memberCount,
    ProjectAccessSource accessSource,
    String projectRoleCode,
    String projectRoleName,
    Set<String> effectivePermissions,
    ProjectCapabilities capabilities,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

record ProjectMemberResponse(
    Long id,
    Long tenantId,
    Long projectId,
    Long userId,
    String nickname,
    String mobile,
    Long roleId,
    String roleName,
    String roleCode,
    String status,
    LocalDateTime joinedAt
) {
}

record ProjectRoleResponse(
    Long id,
    Long tenantId,
    Long projectId,
    String name,
    String code,
    String description,
    Boolean isSystem,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

record ProjectRolePermissionResponse(
    Long id,
    String code,
    String name,
    String resource,
    String action
) {
}

record ProjectDetailResponse(
    ProjectResponse project,
    List<ProjectMemberResponse> members,
    List<ProjectRoleResponse> roles
) {
}
