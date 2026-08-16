package com.antshorttv.rbac;

import java.time.LocalDateTime;

public record RoleResponse(
    Long id,
    Long tenantId,
    String code,
    String name,
    String description,
    String roleType,
    String status,
    Boolean isDefault,
    Long memberCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    static RoleResponse from(RoleEntity role, long memberCount) {
        return new RoleResponse(
            role.getId(),
            role.getTenantId(),
            role.getCode(),
            role.getName(),
            role.getDescription(),
            role.getRoleType(),
            role.getStatus(),
            role.getIsDefault(),
            memberCount,
            role.getCreatedAt(),
            role.getUpdatedAt()
        );
    }
}
