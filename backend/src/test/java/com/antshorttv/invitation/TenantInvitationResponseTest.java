package com.antshorttv.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TenantInvitationResponseTest {

    @Test
    void pendingInvitationPastDeadlineIsReportedAsExpiredWithoutChangingStoredState() {
        TenantInvitationEntity invitation = invitation(InvitationStatus.PENDING, LocalDateTime.now().minusDays(1));

        assertThat(TenantInvitationResponse.from(invitation, null).status()).isEqualTo("EXPIRED");
        assertThat(invitation.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void pendingInvitationBeforeDeadlineRemainsPending() {
        TenantInvitationEntity invitation = invitation(InvitationStatus.PENDING, LocalDateTime.now().plusDays(1));

        assertThat(TenantInvitationResponse.from(invitation, null).status()).isEqualTo("PENDING");
    }

    @ParameterizedTest
    @EnumSource(value = InvitationStatus.class, names = {"ACCEPTED", "REJECTED", "CANCELLED", "EXPIRED"})
    void terminalStatusIsPreservedAfterDeadline(InvitationStatus status) {
        TenantInvitationEntity invitation = invitation(status, LocalDateTime.now().minusDays(1));

        assertThat(TenantInvitationResponse.from(invitation, null).status()).isEqualTo(status.name());
    }

    private TenantInvitationEntity invitation(InvitationStatus status, LocalDateTime deadline) {
        TenantInvitationEntity invitation = new TenantInvitationEntity();
        invitation.setStatus(status.name());
        invitation.setExpiredAt(deadline);
        return invitation;
    }
}
