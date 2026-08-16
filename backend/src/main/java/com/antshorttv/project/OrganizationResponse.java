package com.antshorttv.project;

import java.time.LocalDateTime;
import java.util.List;

record OrganizationResponse(
    Long id,
    Long tenantId,
    Long parentId,
    String name,
    String code,
    Integer level,
    Long leaderId,
    Integer sort,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<OrganizationResponse> children
) {
    static OrganizationResponse from(OrganizationEntity entity, List<OrganizationResponse> children) {
        return new OrganizationResponse(
            entity.id,
            entity.tenantId,
            entity.parentId,
            entity.name,
            entity.code,
            entity.level,
            entity.leaderId,
            entity.sort,
            entity.status,
            entity.createdAt,
            entity.updatedAt,
            children
        );
    }
}
