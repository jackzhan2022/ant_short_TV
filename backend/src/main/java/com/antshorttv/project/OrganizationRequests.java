package com.antshorttv.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateOrganizationRequest(
    Long parentId,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 50) String code,
    Long leaderId,
    Integer sort
) {
}

record UpdateOrganizationRequest(
    Long parentId,
    @NotBlank @Size(max = 100) String name,
    Long leaderId,
    Integer sort
) {
}

record UpdateOrganizationStatusRequest(
    @NotBlank String status
) {
}

record UpdateOrganizationLeaderRequest(
    Long leaderId
) {
}
