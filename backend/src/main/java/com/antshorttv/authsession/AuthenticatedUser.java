package com.antshorttv.authsession;

import java.security.Principal;
import java.time.LocalDateTime;

public record AuthenticatedUser(
    Long userId,
    String mobile,
    String sessionId,
    LocalDateTime expiresAt
) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
