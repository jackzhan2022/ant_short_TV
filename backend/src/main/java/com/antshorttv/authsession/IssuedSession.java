package com.antshorttv.authsession;

import java.time.LocalDateTime;

public record IssuedSession(String credential, String sessionId, LocalDateTime expiresAt) {
}
