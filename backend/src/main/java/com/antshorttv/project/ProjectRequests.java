package com.antshorttv.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

record CreateProjectRequest(
    Long organizationId,
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 50) String code,
    String description,
    String coverUrl,
    String coverSource,
    @NotNull Long ownerId,
    LocalDate startDate,
    LocalDate endDate,
    String aspectRatio,
    String fileFormat,
    String scriptType,
    String breakdownStrength,
    String visualStyle,
    String initialScriptContent
) {
}

record UpdateProjectRequest(
    Long organizationId,
    @NotBlank @Size(max = 200) String name,
    String description,
    String coverUrl,
    String coverSource,
    LocalDate startDate,
    LocalDate endDate,
    String aspectRatio,
    String fileFormat,
    String scriptType,
    String breakdownStrength,
    String visualStyle,
    String initialScriptContent
) {
}

record UpdateProjectStatusRequest(
    @NotBlank String status
) {
}

record UpdateProjectOwnerRequest(
    @NotNull Long ownerId
) {
}

record AddProjectMemberRequest(
    @NotNull Long userId,
    Long organizationId,
    Long roleId
) {
}

record UpdateProjectMemberRoleRequest(
    @NotNull Long roleId
) {
}

record CreateProjectRoleRequest(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    String dataScope,
    List<String> permissionCodes
) {
}

record UpdateProjectRoleRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    String status,
    String dataScope,
    List<String> permissionCodes
) {
}

record UpdateProjectRolePermissionsRequest(
    List<String> permissionCodes
) {
}
