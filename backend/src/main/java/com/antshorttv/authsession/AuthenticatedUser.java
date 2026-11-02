package com.antshorttv.authsession;

import java.security.Principal;
import java.time.LocalDateTime;

public record AuthenticatedUser(
    Long userId,
    String mobile,
    String sessionId,
    LocalDateTime expiresAt,
    Impersonation impersonation
) implements Principal {

    public AuthenticatedUser(Long userId, String mobile, String sessionId, LocalDateTime expiresAt) {
        this(userId, mobile, sessionId, expiresAt, null);
    }

    public record Impersonation(Long administratorId, String administratorName, Long tenantId) {}

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
