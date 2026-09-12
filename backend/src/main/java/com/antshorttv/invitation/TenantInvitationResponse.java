package com.antshorttv.invitation;

import com.antshorttv.tenant.TenantEntity;
import java.time.LocalDateTime;

public record TenantInvitationResponse(
    Long id,
    Long tenantId,
    String tenantName,
    String inviteMobile,
    Long inviteUserId,
    Long invitedBy,
    String token,
    String status,
    LocalDateTime expiredAt,
    LocalDateTime acceptedAt,
    LocalDateTime createdAt
) {

    public static TenantInvitationResponse from(TenantInvitationEntity invitation, TenantEntity tenant) {
        String status = invitation.getStatus();
        if (InvitationStatus.PENDING.name().equals(status)
            && invitation.getExpiredAt() != null
            && !invitation.getExpiredAt().isAfter(LocalDateTime.now())) {
            status = InvitationStatus.EXPIRED.name();
        }
        return new TenantInvitationResponse(
            invitation.getId(),
            invitation.getTenantId(),
            tenant == null ? null : tenant.getName(),
            invitation.getInviteMobile(),
            invitation.getInviteUserId(),
            invitation.getInvitedBy(),
            invitation.getToken(),
            status,
            invitation.getExpiredAt(),
            invitation.getAcceptedAt(),
            invitation.getCreatedAt()
        );
    }
}
