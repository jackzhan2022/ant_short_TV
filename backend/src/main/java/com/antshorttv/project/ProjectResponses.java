package com.antshorttv.project;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

record ProjectResponse(
    Long id,
    Long tenantId,
    Long organizationId,
    String organizationName,
    String name,
    String code,
    String description,
    String coverUrl,
    Long ownerId,
    String ownerName,
    String status,
    LocalDate startDate,
    LocalDate endDate,
    Long memberCount,
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
    Long organizationId,
    String organizationName,
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
    String dataScope,
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
